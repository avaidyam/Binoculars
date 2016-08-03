#include "cp.h"

#include "constants.h"
#include "surface.h"
#include "output.h"
#include <ANN/ANN.h>
#include "MersenneTwister.h"
#include <valarray>

void transform_surface(vector<criticalpoint>&);


void get_alpha_beta(double* cx, criticalpoint& p, double& alpha, double& beta)
{
    double d = get_distance2(cx, p.coords, 3);

    // calculate scalar product n.(x-p)
    double f = 0.;
    for(int i=0;i<3;i++)
        f += (p.normal[i]*(cx[i] - p.coords[i]));
    alpha = sqrt(d - f*f);
    beta = f;

    if(alpha < 0.)
    {
        cerr << "ALPHA cannot be negative" << endl;
        exit(EXIT_FAILURE);
    }
}


double support_angle(double* n1, double* n2)
{
    double a = 0.;
    for(int i=0;i<3;i++)
    {
       a += n1[i]*n2[i];
    }
    if(isnan(a) || isinf(a))
    {
        //cerr << "n1: " << n1[0] << " " << n1[1] << " " << n1[2] << endl;
        //cerr << "n2: " << n2[0] << " " << n2[1] << " " << n2[2] << endl;
        return 0.;
    }
    if (a > 1.0) a = 1.0;
    if (a < -1.0) a = -1.0;
    return acos(a);
}


void build_histogram(vector<opoint>& V, double bin_width)
{
    // calculate the rows and columns
    int rows = 6;
    int cols = 6;

    vector<vector<int> > M(rows);
    for(int i=0;i<rows;i++)
    {
        vector<int> N(cols, 0);
        M[i] = N;
    }

    int a=0, b = 0;
    int j, k;

    for(size_t i=0;i<V.size();i++)
    {
        j = (int) floor((5-V[i].BETA)/bin_width);
        k = (int) floor(V[i].ALPHA/bin_width);

        if(j >= rows) j = rows-1;
        if(k >= cols) k = cols-1;

        // bilinear weighting
        a = (int) floor(V[i].ALPHA - k * bin_width);
        b = (int) floor(V[i].BETA - j * bin_width);

        M[j][k] += (1-a)*(1-b);

        if(j+1 < rows)
            M[j+1][k] += a*(1-b);

        if(k+1 < cols)
            M[j][k+1] += (1-a)*(b);

        if((j+1 < rows) && (k+1 < cols))
            M[j+1][k+1] += a*b;
    }

    cout << rows << " " << cols << " ";
    for(int i=0;i<rows;i++)
    {
        for(int j=0;j<cols;j++)
        {
            cout << M[i][j] << " ";
        }
    }
    cout << endl;
}


void calculate_spin_image(isurface& msurf, double r, double bin_width)
{
    vector<criticalpoint> VCP = msurf.RVCP;
    // enter into the KDTree all points in the surface
    // build list of vertices
    GSList * vertices = NULL;
    gts_surface_foreach_vertex(msurf.isurf, (GtsFunc) build_list, &vertices);

    int N = gts_surface_vertex_number(msurf.isurf);

    ANNpointArray dataPts;
    dataPts = annAllocPts(N, 3);
    ANNpoint f = annAllocPt(3);

    GSList *lst = vertices;
    int i = 0;
    while(lst)
    {
        GtsPoint *p = GTS_POINT (lst->data);

        double xyz[3];
        xyz[0] = p->x;
        xyz[1] = p->y;
        xyz[2] = p->z;

        for(int j=0;j<3;j++)
            dataPts[i][j] = xyz[j];

        i++;
        lst = lst->next;        
    }
    g_slist_free(vertices);

    
    ANNkd_tree *T = new ANNkd_tree(dataPts, N, 3);
    cerr << "Tree done" << endl;

    double alpha, beta;
    double z[3],y[3];

    for(size_t i=0;i<VCP.size();i++)
    {
        ANNidxArray nnIdx = NULL;
        ANNdistArray dists = NULL;
    
        for (int k=0; k<3; k++)
            f[k] = VCP[i].coords[k];
        int nnode = T->annkFRSearch(f, r, 0, nnIdx, dists, 0.); // search for n of them.
        if(nnode <= 0)
            continue;
        
        nnIdx = new ANNidx[nnode];
        dists = new ANNdist[nnode];
        T->annkFRSearch(f, r, nnode, nnIdx, dists, 0.); // search for n of them.

        vector<opoint> V;

        for(int m=0;m<nnode;m++)
        {
            int j = nnIdx[m];
            if(j == VCP[i].cpid) continue;

            z[0] = msurf.srfnormals[j].nx; z[1] = msurf.srfnormals[j].ny; z[2] = msurf.srfnormals[j].nz;

            // check support angle
            if(support_angle(z, VCP[i].normal) > (2.*M_PI/3.))
                continue;

            y[0] = dataPts[j][0]; y[1] = dataPts[j][1]; y[2] = dataPts[j][2];
            get_alpha_beta(y, VCP[i], alpha, beta);

            opoint objno(alpha, beta);
            V.push_back(objno);
        }
        // make the histogram for this oriented point and write to file
        //cerr << V.size() << endl;
        
        build_histogram(V, bin_width);

        delete [] nnIdx;
        delete [] dists;
    }

    // kdtree deletion
    delete T;
    annDeallocPts(dataPts);
}

/************************ TESTING rotation invariance ****************************/
//    * α and γ range from 0 to 2π radians.
//    * β ranges from 0 to π radians.
// http://mathworld.wolfram.com/EulerAngles.html
void rotate_coord(double oldX, double oldY, double oldZ,
                 double& newX, double& newY, double& newZ,
                 double psi, double theta, double phi)
{
    double r11, r21, r31, r12, r22, r32, r13, r23, r33;

    r11 = cos(psi)*cos(phi)-cos(theta)*sin(phi)*sin(psi);
    r12 = cos(psi)*sin(phi) + cos(theta)*cos(phi)*sin(psi);
    r13 = sin(psi)*sin(theta);
    r21 = -sin(psi)*cos(phi) - cos(theta)*sin(phi)*cos(psi);
    r22 = -sin(psi)*sin(phi) + cos(theta)*cos(phi)*cos(psi);
    r23 = cos(psi)*sin(theta);
    r31 = sin(theta)*sin(phi);
    r32 = -sin(theta)*cos(phi);
    r33 = cos(theta);

    newX = r11 * oldX + r12 * oldY + r13 * oldZ;
    newY = r21 * oldX + r22 * oldY + r23 * oldZ;
    newZ = r31 * oldX + r32 * oldY + r33 * oldZ;
}

void transform_surface(vector<criticalpoint>& C)
{
    MTRand objrnd;
    double phi = 0., theta = 0., psi = 0.;

    double tx1, ty1, tz1, oldx, oldy, oldz;
    phi = objrnd.rand(M_PI); theta = objrnd.rand(M_PI); psi = objrnd.rand(M_PI);

    vector<criticalpoint> NC;
    double ntx1, nty1, ntz1, oldnx, oldny, oldnz;
    for(size_t i=0;i<C.size();i++)
    {
        criticalpoint RA = C[i];
        oldx = RA.coords[0];
        oldy = RA.coords[1];
        oldz = RA.coords[2];

        oldnx = RA.normal[0];
        oldny = RA.normal[1];
        oldnz = RA.normal[2];

        rotate_coord(oldx, oldy, oldz, tx1, ty1, tz1, psi, theta, phi);
        rotate_coord(oldnx, oldny, oldnz, ntx1, nty1, ntz1, psi, theta, phi);

        RA.coords[0] = tx1;RA.coords[1] = ty1;RA.coords[2] = tz1;
        RA.normal[0] = ntx1; RA.normal[1] = nty1; RA.normal[2] = ntz1;

        NC.push_back(RA);
    }
    C.clear();
    C = NC;
}

/*
void build_histogram(vector<opoint>& V, double bin_width)
{
    valarray<double> F1(V.size()), F2(V.size());
    for(size_t i=0;i<V.size();i++)
    {
        F1[i] = V[i].ALPHA;
        F2[i] = V[i].BETA;
    }


    double alpha_max = F1.max();
    double beta_max = F2.max(), beta_min = F2.min();

    // calculate the rows and columns
    int rows = (int) floor((beta_max-beta_min)/bin_width);
    int cols = (int) floor(alpha_max/bin_width);

    if(rows == 0)
        rows = rows + 1;
    if(cols == 0)
        cols = cols + 1;


    vector<vector<int> > M(rows);
    for(int i=0;i<rows;i++)
    {
        vector<int> N(cols, 0);
        M[i] = N;
    }

    int a=0, b = 0;
    int j, k;

    for(size_t i=0;i<V.size();i++)
    {
        j = (int) floor((beta_max-V[i].BETA)/bin_width);
        k = (int) floor(V[i].ALPHA/bin_width);


        if(j >= rows) j = rows-1;
        if(k >= cols) k = cols-1;

        a = (int) floor(V[i].ALPHA - k * bin_width);
        b = (int) floor(V[i].BETA - j * bin_width);

        // bilinear weighting
        //cerr << a << " " << b << endl;
    
        M[j][k] += (1-a)*(1-b);

        if(j+1 < rows)
            M[j+1][k] += a*(1-b);

        if(k+1 < cols)
            M[j][k+1] += (1-a)*(b);

        if((j+1 < rows) && (k+1 < cols))
            M[j+1][k+1] += a*b;
    }

    cout << rows << " " << cols << ": ";
    for(int i=0;i<rows;i++)
    {
        for(int j=0;j<cols;j++)
        {
            cout << M[i][j] << " ";
        }
    }
    cout << endl;
}
*/

