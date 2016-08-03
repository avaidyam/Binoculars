// align.cc
//
// Find rmse of rigid body alignment of target and query, 
// matching residues found by clique analysis
//
// Reference:
//   S. Umeyama, 
//   Least-squares estimation of transformation parameters 
//   between two point patterns,
//   IEEE Transactions on Pattern Analysis and Machine Intelligence,
//   Vol 13, No 4, pp 376-380, 1991.
//
// dw, 17/2/4

#include <iostream>
#include <fstream>
#include <vector>
#include <math.h>
#include <float.h>
#include <gsl/gsl_matrix.h>
#include <gsl/gsl_blas.h>
#include <gsl/gsl_linalg.h>

using namespace std;


#include "align.h"


bool debug;


void print_gsl_vector(ostream& os, const gsl_vector *v)
{
    for (size_t i=0; i<v->size-1; i++)
    {
        os << gsl_vector_get(v,i) << " ";
    }
  
    os << gsl_vector_get(v, v->size-1) << endl;
}


void print_gsl_matrix(ostream& os, const gsl_matrix *v)
{
    for(size_t i=0; i<v->size1; i++)
    {
        for(size_t j=0; j<v->size2-1; j++)
        {
            os << gsl_matrix_get(v,i,j) << ", ";
        }
        os << gsl_matrix_get(v,i,v->size2-1) << endl;
    }
}


// find rigid body transformation z=Rx+t minimizing 
// least squares error ||y-z||**2
void align(gsl_matrix_const_view x, gsl_matrix_const_view y, gsl_matrix *R, gsl_vector *t, double *rmse)
{
    const size_t n1 = x.matrix.size1;
    const size_t n2 = x.matrix.size2;
  
    gsl_vector *xmu = gsl_vector_alloc(n2);   // means of columns of x
    gsl_vector *ymu = gsl_vector_alloc(n2);   // means of columns of y
    gsl_matrix *X = gsl_matrix_alloc(n1,n2);  // mean-centred x
    gsl_matrix *Y = gsl_matrix_alloc(n1,n2);  // mean-centred y
  
    // for LU decomposition
    int sign;
    gsl_permutation *p = gsl_permutation_alloc(n2);
    gsl_matrix *LU = gsl_matrix_alloc(n2, n2);
  
    // for SVD
    gsl_vector *work = gsl_vector_alloc(n2);
    gsl_matrix *U = gsl_matrix_alloc(n2,n2);
    gsl_matrix *V = gsl_matrix_alloc(n2,n2);
    gsl_vector *d = gsl_vector_alloc(n2);
  
    // mean-centre the data
    for(size_t j=0; j<n2; j++)
    {
        double mu = 0, nu = 0;
        for(size_t i=0; i<n1; i++)
        {
            mu += gsl_matrix_get(&x.matrix,i,j);
            nu += gsl_matrix_get(&y.matrix,i,j);
        }
        gsl_vector_set(xmu,j,mu/n1);
        gsl_vector_set(ymu,j,nu/n1);
    }
  
    if(debug)
    {
        cerr << "xmu: ";
        print_gsl_vector(cerr,xmu);
        cerr << "ymu: ";
        print_gsl_vector(cerr,ymu);
    }
  
    for(size_t i=0; i<n1; i++)
    {
        for(size_t j=0; j<n2; j++)
        {
            gsl_matrix_set(X,i,j,gsl_matrix_get(&x.matrix,i,j)-gsl_vector_get(xmu,j));
            gsl_matrix_set(Y,i,j,gsl_matrix_get(&y.matrix,i,j)-gsl_vector_get(ymu,j));
        }
    }

    // form covariance matrix U = Y'X
    gsl_blas_dgemm(CblasTrans, CblasNoTrans, 1.0/n1, Y, X, 0.0, U);
  
    // calculate det(covariance matrix) from LU decomposition
    gsl_matrix_memcpy(LU, U);
    gsl_linalg_LU_decomp(LU, p, &sign);
    double det = gsl_linalg_LU_det(LU, sign);
  
    if(debug)
    {
        cerr << "covariance:\n";
        print_gsl_matrix(cerr,U);
        cerr << "det(covariance): " << det << "\n";
    }
  
    // singular value decomposition U -> U*diag(d)*V'
    gsl_linalg_SV_decomp(U, V, d, work) ;
  
    if(debug)
    {
        cerr << "d: ";
        print_gsl_vector(cerr,d);
    }
  
    // find rank of covariance matrix
    size_t rank;
  
    if(gsl_vector_get(d, n2-1) >= FLT_EPSILON)
    {
        rank = n2;
    }
    else if(gsl_vector_get(d, n2-2) >= FLT_EPSILON)
    {
        rank = n2-1;
    }
    else
    {
        cerr << "align: two zero singular values in svd,\n";
        exit(EXIT_FAILURE);        
    }
  
    // calculate detU and detV for rank deficient case
    double detU = 0., detV = 0.;
  
    if(rank == n2-1)
    {
        gsl_matrix_memcpy(LU, U);
        gsl_linalg_LU_decomp(LU, p, &sign);
        detU = gsl_linalg_LU_det(LU, sign);
    
        gsl_matrix_memcpy(LU, V);
        gsl_linalg_LU_decomp(LU, p, &sign);
        detV = gsl_linalg_LU_det(LU, sign);
    
        if(debug)
        {
            cerr << "detU: " << detU << "\n";
            cerr << "detV: " << detV << "\n";
        }
    }
  
    if((rank == n2 && det >= 0) || (rank == n2-1 && detU*detV > 0))
    {
        // R = UV'
        gsl_blas_dgemm(CblasNoTrans, CblasTrans, 1.0, U, V, 0.0, R);
    }
    else
    {
        // R = USV', S = diag(1,...,1,-1)
        gsl_matrix *S = gsl_matrix_alloc(n2,n2);
        gsl_matrix *T = gsl_matrix_alloc(n2,n2);
        gsl_matrix_set_identity(S);
        gsl_matrix_set(S, n2-1, n2-1, -1.0);
        gsl_blas_dgemm(CblasNoTrans, CblasTrans, 1.0, S, V, 0.0, T);
        gsl_blas_dgemm(CblasNoTrans, CblasNoTrans, 1.0, U, T, 0.0, R);
        gsl_matrix_free(S);
        gsl_matrix_free(T);
    }
  
    if(debug)
    {
        cout << "R:\n";
        print_gsl_matrix(cout,R);
    }
  
    // variances
    double sx2 = 0, sy2 = 0;
  
    for(size_t i=0; i<n1; i++)
    {
        for(size_t j=0; j<n2; j++)
            sx2 += gsl_matrix_get(X,i,j) * gsl_matrix_get(X,i,j);
    }
  
    for(size_t i=0; i<n1; i++)
        for(size_t j=0; j<n2; j++)
            sy2 += gsl_matrix_get(Y,i,j) * gsl_matrix_get(Y,i,j);
  
    sx2 /= n1;
    sy2 /= n1;
  
    double trace = 0;
  
    for(size_t i=0; i<n2-1; i++)
        trace += gsl_vector_get(d,i);
  
    if((rank == n2 && det >= 0) || (rank == n2-1 && detU*detV > 0))
    {
        trace += gsl_vector_get(d,n2-1);
    }
    else
    {
        trace -= gsl_vector_get(d,n2-1);
    }
  
    // use next line to include a scaling factor c (z=cRx+t)
    //double c = trace / sx2;
    double c = 1.0;
  
    if(debug)
        cerr << "c: " << c << endl;
  
    // t = -cRxmean + ymean
    gsl_vector_memcpy(t, ymu);
    gsl_blas_dgemv(CblasNoTrans, -c, R, xmu, 1.0, t);

    if(debug)
    {
        cout << "t: ";
        print_gsl_vector(cout, t);
    }
  
    // for exact matches rounding errors can produce mse<0
    double mse = sy2 + c*c*sx2 - 2*c*trace;
    *rmse = (mse > 0) ? sqrt(mse) : 0;

    // release storage
    gsl_vector_free(xmu);
    gsl_vector_free(ymu);
    gsl_matrix_free(X);
    gsl_matrix_free(Y);
    gsl_matrix_free(LU);
    gsl_vector_free(work);
    gsl_matrix_free(U);
    gsl_matrix_free(V);
    gsl_vector_free(d);
    gsl_permutation_free(p);
}


// align target and query using vertices in clique
double align(vector<atom>& BR, vector<atom>& UR, map<int, int>& MRES_R, int p1, vector<atom>& BL, 
    vector<atom>& UL, map<int, int>& MRES_L, int p2)
{
    const size_t n1 = p1 + p2;
    const size_t n2 = 3;
  
    // storage for coords
    double *x = new double[n1*n2];    // query
    double *y = new double[n1*n2];    // target
  
    // storage for the alignment transformation
    gsl_matrix *R = gsl_matrix_alloc(n2,n2);
    gsl_vector *t = gsl_vector_alloc(n2);
  
    // copy critical point coords to arrays x and y
	map<int, int>::iterator it1;
	int k1, k2;
	size_t i=0;
	for(it1=MRES_R.begin();it1!=MRES_R.end();++it1)
	{
		k1 = it1->first;
		k2 = it1->second;
		if(k2 == -1) continue;
        for(size_t j=0; j<n2; j++)
        {
            x[i*n2+j] = BR[k1].axyz[j];
            y[i*n2+j] = UR[k2].axyz[j];
        }
		i++;
	}
	
	for(it1=MRES_L.begin();it1!=MRES_L.end();++it1)
	{
		k1 = it1->first;
		k2 = it1->second;
		if(k2 == -1) continue;
        for(size_t j=0; j<n2; j++)
        {
            x[i*n2+j] = BL[k1].axyz[j];
            y[i*n2+j] = UL[k2].axyz[j];
        }
		i++;
	}
	
    // take gsl matrix views of x and y arrays
    gsl_matrix_const_view xv = gsl_matrix_const_view_array(x,n1,n2);
    gsl_matrix_const_view yv = gsl_matrix_const_view_array(y,n1,n2);
  
    if(debug)
    {
        cerr << "x:\n";
        print_gsl_matrix(cout,&xv.matrix);
        cerr << "y:\n";
        print_gsl_matrix(cout,&yv.matrix);
    }
  
    // call the real alignment function
    double rmse;
    align(xv, yv, R, t, &rmse);

  
    if(debug)
        cerr << "rmse = " << rmse << "\n";
    // release storage
    gsl_matrix_free(R);
    gsl_vector_free(t);
    delete [] x;
    delete [] y;

    return rmse;
}



