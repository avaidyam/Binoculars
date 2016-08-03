#include "cp.h"

#include "constants.h"
#include "surface.h"
#include "output.h"
#include <ANN/ANN.h>
#include <valarray>


// construct histogram of data in values[N] array
void build_shape_histogram(vector<double>& X)
{
    int nbins = 10;
    vector<double> Z(nbins, 0.);

    valarray<double> L(X.size());
    for(size_t i=0;i<X.size();i++)
    {
        L[i] = X[i];
    }
    double min_sample = L.min();
    double max_sample = L.max();

    double bin_size = (max_sample - min_sample)/(double)nbins;

    //put the data into bins
    int pos = 0;
    for(size_t i=0;i<X.size();i++)
    {
        pos = (int)floor((X[i]-min_sample)/bin_size);
        if(pos >= nbins)
            pos = pos - 1;
        Z[pos] += 1.;
    }

    cout << fixed << setprecision(4);
    for(int i=0;i<nbins;i++)
    {
        Z[i] /= (double)X.size();
        cout << setw(8) << left << Z[i] << " ";
    }
    cout << endl;
    
}

void calculate_shape_distribution(isurface& msurf, double r)
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

        vector<double> V;

        for(int m=0;m<nnode;m++)
        {
            int j = nnIdx[m];
            if(j == VCP[i].cpid) continue;

            //cerr << j << " " << msurf.si_value[j] << endl;

            V.push_back(msurf.curv_value[j]);
        }
        // make the histogram for this oriented point and write to file
        //cerr << V.size() << endl;
        if(V.size() > 0)
            build_shape_histogram(V);

        V.clear();

        delete [] nnIdx;
        delete [] dists;
    }

    // kdtree deletion
    delete T;
    annDeallocPts(dataPts);
}

