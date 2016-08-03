/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 */


#include "surface.h"
#include "options.h"
#include <iostream>
#include <limits>
#include <iomanip>

double SMOOTH_F;


// a vertex normal at a vertex of a polyhedron is the normalized average
// of the surface normals of the faces that contain that vertex.
void get_vertex_normal(GtsSurface* s, GtsVertex* v, double *nrm)
{
    GSList * faces = gts_vertex_faces (v, s, NULL);
    if(faces == NULL)
    {
        cerr << "NULL face list" << endl;
        exit(EXIT_FAILURE);
    }

    for(int j=0;j<3;j++)
        nrm[j] = 0.;


    GSList *i = faces;
    while(i)
    {
        GtsFace * f = GTS_FACE(i->data);
        double nx, ny, nz;
        gts_triangle_normal(GTS_TRIANGLE(f), &nx, &ny, &nz);
        double len = sqrt(nx*nx + ny*ny + nz*nz);
        nrm[0] += nx/len;
        nrm[1] += ny/len;
        nrm[2] += nz/len;
        i = i-> next;
    }
    g_slist_free(faces);

    double len = sqrt(nrm[0]*nrm[0] + nrm[1]*nrm[1] + nrm[2]*nrm[2]);
    for(int j=0;j<3;j++)
        nrm[j] /= len;
}


void vertex_cleanup (GtsVertex * v)
{
    gts_vertex_is_contact (v, TRUE);
}

void edge_cleanup (GtsSurface * surface)
{
    GSList * edges = NULL;
    GSList * i;

    // build list of edges
    gts_surface_foreach_edge (surface, (GtsFunc) build_list, &edges);

    // We want to control manually the destruction of edges
    gts_allow_floating_edges = TRUE;

    i = edges;
    while (i)
    {
        GtsEdge * e = GTS_EDGE(i->data);
        GtsEdge * duplicate;
        if (GTS_SEGMENT (e)->v1 == GTS_SEGMENT (e)->v2) // edge is degenerate
        // destroy e
        gts_object_destroy (GTS_OBJECT (e));
        else if ((duplicate = gts_edge_is_duplicate (e)))
        {
            // replace e with its duplicate
            gts_edge_replace (e, duplicate);
            // destroy e
            gts_object_destroy (GTS_OBJECT (e));
        }
        i = i->next;
    }

    gts_allow_floating_edges = FALSE;
    g_slist_free (edges);
}


void triangle_cleanup (GtsSurface * s)
{
    GSList * triangles = NULL;
    GSList * i;

    // build list of triangles
    gts_surface_foreach_face (s, (GtsFunc) build_list, &triangles);

    // remove duplicate triangles
    i = triangles;
    while(i)
    {
        GtsTriangle * t = GTS_TRIANGLE(i->data);
        if(gts_triangle_is_duplicate (t))
        // destroy t, its edges (if not used by any other triangle)
        // and its corners (if not used by any other edge)
        gts_object_destroy (GTS_OBJECT (t));
        i = i->next;
    }

    // free list of triangles
    g_slist_free (triangles);
}

void surface_clean(GtsSurface *s, double cutoff)
{
    gts_surface_foreach_vertex (s, (GtsFunc) vertex_cleanup, NULL);
    // eliminate degenerate and duplicate edges
    edge_cleanup(s);
    // eliminate duplicate triangles
    triangle_cleanup (s);

    if(cutoff == 0)
    {
        //cerr << "No surface reduction for cutoff = " << cutoff << endl;
        return;
    }

    //cerr << "debug: Reducing surface ..." << endl;

    GtsKeyFunc cost_func = NULL;
    gpointer cost_data = NULL;
    GtsCoarsenFunc coarsen_func = NULL;
    gpointer coarsen_data = NULL;
    GtsStopFunc stop_func = NULL;
    gpointer stop_data = NULL;

    stop_func = (GtsStopFunc) gts_coarsen_stop_cost;
    gdouble cost_max = cutoff;
    stop_data = &cost_max;

    gdouble fold = M_PI/180.;

    GtsVolumeOptimizedParams VOP = {0.5, 0.5, 1e-06};
    coarsen_func = (GtsCoarsenFunc) gts_volume_optimized_vertex;
    coarsen_data = &VOP;

    cost_func = (GtsKeyFunc) gts_volume_optimized_cost;    
    cost_data = &VOP;

    gts_surface_coarsen (s, cost_func, cost_data, coarsen_func, coarsen_data, stop_func, stop_data, fold);
}


void print_stats(GtsSurface *s)
{
    //GtsBBox * bb = gts_bbox_surface (gts_bbox_class (), s);
    gts_surface_print_stats (s, stderr);
    //fprintf (stderr, "# Bounding box: [%g,%g,%g] [%g,%g,%g]\n",
    //     bb->x1, bb->y1, bb->z1,
    //     bb->x2, bb->y2, bb->z2);
    cerr << "Volume: " << gts_surface_volume(s) << endl;
    cerr << "Area  : " << gts_surface_area(s) << endl;
}

void point_normalise(double* R, int sz)
{
    double rnorm = 0.;
    int i;
    for(i=0;i<sz;i++)
        rnorm += R[i]*R[i];
    rnorm = sqrt(rnorm);
    for(i=0;i<sz;i++)
        R[i] /= rnorm;
}

double get_mep(double* p, vector<atom>& A)
{
    double qval = 0.;
    for(size_t i=0;i<A.size();i++)
    {
        double dist = get_distance(p, A[i].axyz, 3);
        qval += (A[i].atom_charge/dist);
    }
    return qval;
}

// a vertex normal at a vertex of a polyhedron is the normalized average
// of the surface normals of the faces that contain that vertex.
void get_vertex_normal(isurface& msurf, GtsPoint* v, double *nrm, double sm)
{
    for(int j=0;j<3;j++)
        nrm[j] = 0.;
    double p[3];
    p[0] = v->x;
    p[1] = v->y;
    p[2] = v->z;
    get_surf_grad_gorin(p, nrm, msurf.atoms, sm);
    point_normalise(nrm, 3);
}

void get_vneighbours(isurface& msurf)
{
    GSList * vertices = NULL;
    gts_surface_foreach_vertex(msurf.isurf, (GtsFunc) build_list, &vertices);
    GSList *m = vertices;
    int i = 0;
    while(m)
    {
        GtsPoint *v = GTS_POINT(m->data);
        GSList *list = gts_vertex_neighbors(GTS_VERTEX(v), NULL, NULL);
        GSList *nv = list;
        vector<int> Z;
        while(nv)
        {
            GtsPoint *p = GTS_POINT(nv->data);
            int pos = g_slist_index(vertices, GTS_VERTEX(p));
            Z.push_back(pos);
            nv = nv->next;
        }
        msurf.NN_MAP[i] = Z;
        m = m->next;
        i++;
    }
    g_slist_free(vertices);
}

void add_point(vector<int>& Z, int pt)
{
    vector<int>::iterator pos;
    pos = find(Z.begin(), Z.end(), pt);
    if(pos == Z.end())
        Z.push_back(pt);
}


void build_list (gpointer data, GSList ** list)
{
  *list = g_slist_prepend (*list, data);
}


void foreach_vertex(GtsVertex * v, gpointer * data)
{
    double cur = 0.;
    GtsSurface *s = (GtsSurface *) data[2];
    if(!gts_vertex_is_boundary (v, s))
        gts_vertex_gaussian_curvature (v, s, &cur);
    
    if(isinf(cur) || isnan(cur))
    {
        cerr << "Curvature values NaN" << endl;
        exit(EXIT_FAILURE);
    }
    //cerr << cur << endl;

    int k = *((int *) data[0]);
    (*((vector<double> *) data[1]))[k] = cur;
    (*((int *) data[0]))++;
}


void compute_property_values(isurface& msurf, double sm)
{
    gpointer data[3];

    int nV = gts_surface_vertex_number(msurf.isurf);
    vector<double> shape_values(nV);
    vector<double> curved_values(nV);
    int k = 0;
    data[0] = &k;
    data[1] = &shape_values;
    data[2] = msurf.isurf;
  
    gts_surface_foreach_vertex(msurf.isurf, (GtsFunc) foreach_vertex, data);

    msurf.cur_value = shape_values;
}


double get_distance(double* v1, double* v2, int len)
{
    double f0 = v1[0] - v2[0];
    double f1 = v1[1] - v2[1];
    double f2 = v1[2] - v2[2];
    f0 *= f0;f1 *= f1; f2 *= f2;
    double d = sqrt(f0 + f1 + f2);
    return d;
}

double get_distance2(double* v1, double* v2, int len)
{
    double f0 = v1[0] - v2[0];
    double f1 = v1[1] - v2[1];
    double f2 = v1[2] - v2[2];
    f0 *= f0;f1 *= f1; f2 *= f2;
    double d = f0 + f1 + f2;
    return d;
}


void implicit_surface(gdouble **f, GtsCartesianGrid g, guint k, gpointer data)
{
    double x, y, z = g.z;
    size_t i, j;
    double func = 0.0;
	
    vector<atom> atms = *((vector<atom> *) data);

    double p[3];
    vector<atom>::iterator atom_iter;

    double Z = -1.*SMOOTH_F;

    for(i = 0, x = g.x; i < g.nx; i++, x += g.dx)
    {
        for(j = 0, y = g.y; j < g.ny; j++, y += g.dy)
        {
            func = 0.0;
            for(atom_iter = atms.begin();atom_iter != atms.end();++atom_iter)
            {
                atom a = *atom_iter;
                if(a.atype.at(0) == 'H')
                    continue;
                p[0] = x; p[1] = y; p[2] = z;
                double t = get_distance2(p, a.axyz, 3);
                func += exp(Z*t);
            }
            f[i][j] = func;
        }
    }
}



void get_grid(vector<atom>& A, double* bbox)
{
    bbox[0] = bbox[2] = bbox[4] = G_MAXDOUBLE;
    bbox[1] = bbox[3] = bbox[5] = -G_MAXDOUBLE;

    for(size_t i=0;i<A.size();i++)
    {
        atom p = A[i];
        if(p.atype.at(0) == 'H')
            continue;

        double m_R = 6.0;

        double m_X = p.axyz[0];

        // if less than 0 or the current value of X_MIN set value to m_X
        if((m_X - m_R) < bbox[0])
            bbox[0] = m_X - m_R;

        if((m_X + m_R) > bbox[1])
            bbox[1] = m_X + m_R;

    
        double m_Y = p.axyz[1];

        // if less than 0 or the current value of Y_MIN set value to m_Y
        if((m_Y - m_R) < bbox[2])
            bbox[2] = m_Y - m_R;
    
        // if > 0 or the current value of Y_MAX set value to m_Y
        if((m_Y + m_R) > bbox[3])
            bbox[3] = m_Y + m_R;

        double m_Z = p.axyz[2];
    
        // if < 0 or the current value of Z_MIN set value to m_Z
        if((m_Z - m_R) < bbox[4])
            bbox[4] = m_Z - m_R;

        // if > 0 or the current value of Z_MAX set value to m_Z
        if((m_Z + m_R) > bbox[5])
            bbox[5] = m_Z + m_R;
    }
}


void create_surface(isurface& msurf, double sm, double cutoff)
{
    double grid[6];
    get_grid(msurf.atoms, grid);

    // calculate number of grid cells along each coordinate axis
    GtsCartesianGrid g;
    double rX = fabs(grid[1] - grid[0]);
    double rY = fabs(grid[3] - grid[2]);
    double rZ = fabs(grid[5] - grid[4]);

    g.x = grid[0]; g.dx = 1.4;
    g.y = grid[2]; g.dy = 1.4;
    g.z = grid[4]; g.dz = 1.4;
    g.nx = 1 + (int) (rX/g.dx);
    g.ny = 1 + (int) (rY/g.dy);
    g.nz = 1 + (int) (rZ/g.dz);
    SMOOTH_F = sm;

    GtsIsoCartesianFunc func = implicit_surface;
    
    msurf.isurf = gts_surface_new (gts_surface_class (),
                    gts_face_class (),
                    gts_edge_class (),
                    gts_vertex_class ());


    gts_isosurface_tetra(msurf.isurf, g, func, &msurf.atoms, SMOOTH_F);
    //gts_isosurface_cartesian(msurf.isurf, g, func, &msurf.atoms, SMOOTH_F);

    if(!gts_surface_is_closed(msurf.isurf))
    {
        cerr << "create_surface: surface not closed" << endl;
        exit(EXIT_FAILURE);
    }
    
    //gts_surface_print_stats (msurf.isurf, stderr);

    surface_clean(msurf.isurf, cutoff);
    
    //gts_surface_print_stats (msurf.isurf, stderr);

    //cerr << "debug: Surface computed..." << endl;
    //print_stats(msurf.isurf);
}


