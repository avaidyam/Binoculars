/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 *  NOTE: Calculation of vertex curvature/MEP etc. is done only once
 *        Calculation of vertex neighbours within a certain radius is
 *        also done only once
 */

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>

#include <map>


#include "cp.h"
#include "constants.h"
#include "surface.h"
#include "output.h"
#include <ANN/ANN.h>

#define ABS(a) (((a) < 0) ? -(a) : (a))



bool find_point(set<int>& V, int k)
{
    set<int>::iterator pos;
    pos = V.find(k);
    if(pos == V.end())
        return false;
    return true;
}

void get_cp(isurface& msurf, double sm)
{
    // build list of vertices
    GSList * vertices = NULL;
    gts_surface_foreach_vertex(msurf.isurf, (GtsFunc) build_list, &vertices);

    GSList *lst = vertices;
    int i = 0;

    vector<criticalpoint> VCP;

    while(lst)
    {
        int type = msurf.VTYPE_MAP[i];
        if(type == -1)
        {
            i++;
            lst = lst->next;
            continue;
        }

        GtsPoint *p = GTS_POINT (lst->data);

        double xyz[3];
        xyz[0] = p->x;
        xyz[1] = p->y;
        xyz[2] = p->z;

        double nrm[3];
        get_vertex_normal(msurf, p, nrm, sm);

        double mepval = get_mep(xyz, msurf.atoms);

        criticalpoint ncp(i, type, xyz, nrm, msurf.cur_value[i], mepval);
        VCP.push_back(ncp);

        i++;
        lst = lst->next;
    }
    g_slist_free(vertices);

    msurf.RVCP = VCP;
}


bool check_saddle(int idx_gt, int idx_lt)
{
    bool retval = false;
    if(idx_gt > 0 && idx_lt > 0)
    {
        if(ABS(idx_gt - idx_lt)%2 == 0)
        {
            if(ABS(idx_gt - idx_lt) >= 4)
            {
                retval = true;
            }
        }
    }
    return retval;    
}


/**
 * Vertex classification based on 
 * Fair Morse Functions for Extracting the Topological Structure of a Surface
 * Mesh-> Xinlai Ni, Michael Garland, John C. Hart
 * Assuming for each edge in the mesh, f(v1) != f(v2)
 * Let Lk(v) be the graph of m vertices (v1,..vm) that share an edge with v
 * edges include (v1,v2), (v2,v3),...(vm,v1)
 * Decompose Lk(v) = Lk(+v) U Lk(-v) U Lk(+/-v)
 * Lk(+v) -> upper link containing vertices {vi -> Lk(v): f(vi) > f(v)}
 * Lk(-v) -> lower link containing vertices {vi -> Lk(v): f(vi) < f(v)}
 * Lk(+/-v) -> mixed edges {[v(+), v(-)]: f(v+) > f(v) > f(v-)}
 * A vertex is labelled a maximum/minimum if its function value is higher
 * or lower than that of its neighbours
 * regular if its lower neighbours form a connected chain
 * saddle otherwise
 */
 
// maxima if f decreases in all directions
// minima if f increases in all directions
// saddle if f switches between increasing and decreasing 2n (n=2,3...) around p

void classify_vertex(isurface& msurf)
{
    int maxtype = 2;
    int mintype = 1;
    int sadtype = 3;
    int regtype = -1;
    int nv = gts_surface_vertex_number(msurf.isurf);
    
    int nsad = 0, nmin = 0, nmax = 0;

    map<int, int> T;

    for(int i=0;i<nv;i++)
    {
        double vval = msurf.cur_value[i];
        vector<int> NB = msurf.NN_MAP[i];
        int idx_gt = 0;
        int idx_lt = 0;

        for(size_t k=0;k<NB.size();k++)
        {
            double pval = msurf.cur_value[NB[k]];
            if(pval > vval)
                idx_gt++;
            if(pval < vval)
                idx_lt++;
        }

        if(idx_lt == 0)
        {
            T[i] = mintype;
            nmin++;
            continue;
        }
        else if(idx_gt == 0)
        {
            T[i] = maxtype;
            nmax++;
            continue;
        }        
        else if(check_saddle(idx_gt, idx_lt))
        {
            T[i] = sadtype;
            nsad++;
            continue;
        }
        else
            T[i] = regtype;
    }
    
    //cerr << "Vertices: " << nv 
    //     << " Maxima: " << nmax
    //     << " Minima: " << nmin
    //     << " Saddles: " << nsad
    //     << endl;

    msurf.VTYPE_MAP = T;
}



void trim_points(vector<criticalpoint>& VCP, double DCUT)
{
    int N = (int) VCP.size();
    ANNpointArray dataPts;
    dataPts = annAllocPts(N, 3);
    
    for(int i=0; i<N; i++)
    {
        for(int j=0;j<3;j++)
            dataPts[i][j] = VCP[i].coords[j];
    }
    ANNkd_tree *T = new ANNkd_tree(dataPts, N, 3);

   
    set<int> cp_removed;
    ANNpoint f = annAllocPt(3);

    for(size_t j=0;j<VCP.size();j++)
    {
        if(find_point(cp_removed, j)) continue;

        ANNidxArray nnIdx = NULL;
        ANNdistArray dists = NULL;
    
        for (int k=0; k<3; k++)
            f[k] = VCP[j].coords[k];
        int nnode = T->annkFRSearch(f, DCUT*DCUT, 0, nnIdx, dists, 0.); // search for n of them.
        if(nnode <= 0)
            continue;
        
        nnIdx = new ANNidx[nnode];
        dists = new ANNdist[nnode];
        T->annkFRSearch(f, DCUT*DCUT, nnode, nnIdx, dists, 0.); // search for n of them.

        for(int m=0;m<nnode;m++)
        {
            int k = nnIdx[m];
            if(k == ANN_NULL_IDX)
                break;
            if((int)j == k)
                continue;
            if(find_point(cp_removed, k)) continue;

            if(VCP[j].CURV > 0. &&  VCP[k].CURV > 0.)
            {
                if(VCP[j].CURV > VCP[k].CURV)
                    cp_removed.insert(k);
                else
                    cp_removed.insert(j);
            }
            if(VCP[j].CURV < 0. &&  VCP[k].CURV < 0.)
            {
                if(VCP[j].CURV < VCP[k].CURV)
                    cp_removed.insert(k);
                else
                    cp_removed.insert(j);
            }
        }
        delete [] nnIdx;
        delete [] dists;
    }
    // kdtree deletion
    delete T;
    annDeallocPts(dataPts);

    vector<criticalpoint> NVCP;
    for(size_t m=0;m<VCP.size();m++)
    {
        if(find_point(cp_removed, m)) continue;
        NVCP.push_back(VCP[m]);
    }

    cerr << "Original: " << VCP.size() << " Reduced: " << NVCP.size() << endl;
    VCP = NVCP;
}



bool vsort(const vindex& C1, const vindex& C2)
{
    return C1.DIST < C2.DIST;
}


string to_string (int t)
{
    stringstream ss;
    ss << t;
    return ss.str();
}


void find_nearest_residue(isurface& msurf, int k)
{
    vector<vindex> V;
    for(size_t i=0;i<msurf.atoms.size();i++)
    {
        if(msurf.atoms[i].satom == 1)
        {
            double dist = get_distance(msurf.atoms[i].axyz, msurf.RVCP[k].coords, 3);
            vindex vi(i, dist);
            V.push_back(vi);
        }
    }
    if(V.size() > 0)
    {
        sort(V.begin(), V.end(), vsort);
        msurf.RVCP[k].nres = msurf.atoms[V[0].ID].residue +
                            to_string(msurf.atoms[V[0].ID].rnum);
    }
    else
    {
        cerr << "find_nearest_residue: 0 size vector" << endl;
        exit(EXIT_FAILURE);
    }
}


void set_nearest_residue(isurface& msurf)
{
    for(size_t i=0;i<msurf.RVCP.size();i++)
    {
        find_nearest_residue(msurf, (int)i);
    }
}

double get_cutoff(int ncp)
{
    if(ncp >= 4000 && ncp < 8000)
    {
        return 2.2;
    }
    if(ncp >= 8000 && ncp < 12000)
    {
        return 2.4;
    }
    if(ncp >= 12000 && ncp < 15000)
    {
        return 2.6;
    }
    if(ncp >= 15000)
    {
        return 2.8;
    }

    return 2.0;// default
}


void calculate_cp(isurface& msurf, double smooth)
{
    compute_property_values(msurf, smooth);

    // classify vertices using classifyVertex
    //cerr << "debug: classifying vertices" << endl;
    classify_vertex(msurf);

    get_cp(msurf, smooth);

    vector<criticalpoint> X = msurf.RVCP;

    double cutoff = get_cutoff(X.size());
    trim_points(X, cutoff);

    msurf.RVCP = X;

    //cerr << "debug: assigning residues" << endl;
    set_nearest_residue(msurf);
}


