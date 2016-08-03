// align.h
//
// prototypes for align.cc
//
// dw, 12/12/3

#include <gsl/gsl_matrix.h>

#include "pdb.h"
#include <map>

// find rigid body transformation z=Rx+t minimizing 
// least squares error ||y-z||**2
// find rigid body transformation z=Rx+t minimizing 
// least squares error ||y-z||**2
void align(gsl_matrix_const_view , gsl_matrix_const_view, gsl_matrix *, gsl_vector *, double *);

double align(vector<atom>&, vector<atom>&, map<int, int>&, int, vector<atom>&,
    vector<atom>&, map<int, int>&, int);

