#include "surface.h"
#include "constants.h"

double SMOOTH_1;
double SMOOTH_2;
double SMOOTH_4;
double SMOOTH;


double get_implicit_function_gorin(double* p, atom& a)
{
	double r = get_distance2(p, a.axyz, 3);
    return (exp(SMOOTH_1 * r));
}


double get_surf_value_gorin(double* p, vector<atom>& A, double sm)
{
    double I = 0.;
    vector<atom>::iterator atom_iter;
    for(atom_iter = A.begin();atom_iter != A.end();++atom_iter)
    {
        atom a = *atom_iter;
        if(a.atype.at(0) == 'H') continue;
        // create the implicit function with the current atom coordinates
        // and atom radius for the x,y,z
        I += get_implicit_function_gorin(p, a);
    }
    return (I - sm);
}

/*
  f(x) = -D*ln(P(x))-C
  f'(x) = -D*P'(x)/P(x);
  P(x) = sum[atoms Imp Func]
  P'(x) = exp(g(x)).g'(x)
  
*/


void get_surf_grad_gorin (double* p, double* grad, vector<atom>& A, double sm)
{
    // distances from x to atom centres
    map<int, double> pval; // stores exponential part
    SMOOTH_1 = -1*sm;
    SMOOTH_2 = -2*sm;
    SMOOTH_4 = 4*sm*sm;
  
    vector<atom>::iterator atom_iter;
    int id = 0;
    for(atom_iter = A.begin();atom_iter != A.end();++atom_iter, id++)
    {
        atom a = *atom_iter;
        if(a.atype.at(0) == 'H') continue;
        pval[id] = get_implicit_function_gorin(p, a);// store exp(---)
    }
  
    for(int i=0;i<3;i++)
    {
        grad[i] = 0;
        id = 0;
        for(atom_iter = A.begin();atom_iter != A.end();++atom_iter, id++)
        {
            atom a = *atom_iter;
            if(a.atype.at(0) == 'H') continue;
            double val = pval[id];
            grad[i] += (SMOOTH_2*val*(p[i] - a.axyz[i]));
        }
    }
}

void get_surf_hess_gorin (double* p, T3Matrix hess, vector<atom>& A, double sm)
{
    // distances from x to atom centres
    map<int, double> pval; // stores exponential part

    SMOOTH_1 = -1*sm;
    SMOOTH_2 = -2*sm;
    SMOOTH_4 = 4*sm*sm;
 
    vector<atom>::iterator atom_iter;
    int id = 0;
    for(atom_iter = A.begin();atom_iter != A.end();++atom_iter, id++)
    {
        atom a = *atom_iter;
        if(a.atype.at(0) == 'H') continue;
        pval[id] = get_implicit_function_gorin(p, a);// store exp(---)
    }
  
    double term4 = 0.;
  
    // hessian
    for(int i=0;i<3;i++)
    {
        for(int j=0;j<3;j++)
        {
            hess[i][j] = 0.;
            term4 = 0.;
            id = 0;
            for(atom_iter = A.begin();atom_iter != A.end();++atom_iter, id++)
            {
                atom a = *atom_iter;
                if(a.atype.at(0) == 'H') continue;
                double term1 = pval[id];// exp eval
                double term2 = (p[i]-a.axyz[i])*(p[j]-a.axyz[j]);
	            double term3 = 0.;
                if (i==j)
                    term3 = (SMOOTH_4*term2 + SMOOTH_2)*term1;
                else
                    term3 = SMOOTH_4*term2* term1;
                term4 += term3;//sum(d2T/dv2)
            }
            hess[i][j] = term4;
        } // end for j
    }// end for i
}
