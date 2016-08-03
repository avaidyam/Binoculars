// align.h
//
// prototypes for align.cc
//
// dw, 12/12/3
#ifndef _PTALIGN_H_
#define _PTALIGN_H_

#include <gsl/gsl_matrix.h>

#include <vector>
#include <cmath>
#include <map>
#include "pdb.h"


typedef double T3Matrix[3][3];

// find rigid body transformation z=Rx+t minimizing 
// least squares error ||y-z||**2
bool ptalign(gsl_matrix_const_view , gsl_matrix_const_view, gsl_matrix *, gsl_vector *, double *);

bool ptalign(vector<vector<double> >&, vector<vector<double> >&, double*);


#endif
