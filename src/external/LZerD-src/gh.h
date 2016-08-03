#include <iostream>
#include <string>
#include <vector>
#include <cmath>
#include <valarray>
#include <map>
#include <set>


#include <stdio.h>
#include <string.h>
#include <math.h>
#include <stdlib.h>


using namespace std;


#include "basis.h"
#include "bvertex.h"
#include "constants.h"
#include "cp.h"
#include "pdb.h"
#include "utils.h"
#include "docksolution.h"
#include <ANN/ANN.h>


typedef valarray<double> Vertex;


/* gh */
void dock(vector<cp>&, vector<atom>&, vector<cp>&,
    vector<atom>&, double, double,
    double, double, double, int, double, string);
void transform_atoms(Transform, vector<atom>&);
void print_vector(vector<int>&);
void transform_points(Transform, vector<cp>&, vector<cp>&);


/* matrix */
void matrix3_inverse(T3Matrix, T3Matrix);
void matrix_inverse(Transform, Transform, double*);
void matrix_prod(Transform, Transform, Transform);

/* merge */
void merge_cp(vector<vector<cp> >&, vector<cp>&);


/* bipartite */
bool verify_transformation(vector<bvertex>&, vector<bvertex>&, vector<int>&, vector<int>&,
    basis&, basis&, vector<vector<double> >&, vector<vector<double> >&,
    vector<vector<double> >&, vector<vector<double> >&,
    vector<cp>&, vector<cp>&, Transform);
bool verify_alignment(vector<bvertex>&, vector<bvertex>&, vector<int>&, vector<int>&,
    basis&, basis&, vector<vector<double> >&, vector<vector<double> >&,
    vector<vector<double> >&, vector<vector<double> >&,
    vector<cp>&, vector<cp>&, Transform, int);
bool find_point(vector<int>&, int);

/* zd */
void calculate_correlation_values(vector<cp>&, vector<cp>&, vector<vector<double> >&);
void calculate_euclidean_distances(vector<cp>&, vector<cp>&, vector<vector<double> >&);
double zd_correlation(vector<double>&, vector<double>&);

/* score */
double get_excluded_volume(vector<atom>&, vector<atom>&, ANNkd_tree*);
double get_sasa(vector<atom>&, vector<atom>&);
double calculate_sas(vector<atom>&);
void get_score(vector<cp>&, vector<cp>&, vector<vector<double> >&, ANNkd_tree*, double&, double&);
