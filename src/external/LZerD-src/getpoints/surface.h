/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 */


#ifndef _SURFACE_H_
#define _SURFACE_H_

#include "classes.h"
#include "constants.h"

typedef double T3Matrix[3][3];

void create_surface(isurface&, double, double);
double get_distance(double*, double*, int);
double get_distance2(double*, double*, int);
void compute_property_values(isurface&, double);


void build_list(gpointer, GSList **);

void get_vertex_normal(isurface&, GtsPoint*, double*, double);
void print_stats(GtsSurface *);
void surface_clean(GtsSurface *, double);
void get_vertex_normal(GtsSurface*, GtsVertex*, double *);
void get_vneighbours(isurface&);


double get_surf_value_gorin(double*, vector<atom>&);
void get_surf_grad_gorin (double*, double*, vector<atom>&, double);
void get_surf_hess_gorin (double*, T3Matrix, vector<atom>&, double);

void get_shapeindex_curvedness(double*, vector<atom>&, double, double&, double&);

double get_mep(double*, vector<atom>&);


#endif
