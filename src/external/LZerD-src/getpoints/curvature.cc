#include "surface.h"

const double M_PI_INV = 1./M_PI;
const double M_PI_INV2 = 2./M_PI;

double get_gaussiancurv_value(double* grad, T3Matrix hess)
{
    double denominator = grad[0]*grad[0] + grad[1]*grad[1] + grad[2]*grad[2];
    denominator *= denominator;
  
    double numerator = grad[0]*grad[0]*(hess[1][1]*hess[2][2] - hess[1][2]*hess[1][2]) +
        grad[1]*grad[1]*(hess[0][0]*hess[2][2] - hess[0][2]*hess[0][2]) +
        grad[2]*grad[2]*(hess[0][0]*hess[1][1] - hess[0][1]*hess[0][1]) +
        2*grad[0]*grad[1]*(hess[0][2]*hess[1][2] - hess[0][1]*hess[2][2]) +
        2*grad[1]*grad[2]*(hess[0][1]*hess[0][2] - hess[1][2]*hess[0][0]) +
        2*grad[0]*grad[2]*(hess[0][1]*hess[1][2] - hess[0][2]*hess[1][1]);
  
    double K = numerator/denominator;
  
    return K;
}


double get_meancurv_value(double* grad, T3Matrix hess)
{
    double denominator = grad[0]*grad[0] + grad[1]*grad[1] + grad[2]*grad[2];
    denominator = 2*pow(denominator, 1.5);
  
    double numerator = hess[0][0]*(grad[1]*grad[1] + grad[2]*grad[2]) + hess[1][1]*(grad[0]*grad[0]
            + grad[2]*grad[2]) + hess[2][2]*(grad[0]*grad[0] + grad[1]*grad[1]) -
            2*(grad[0]*grad[1]*hess[0][1] + grad[1]*grad[2]*hess[1][2] + grad[0]*grad[2]*hess[0][2]);
  
    double H = numerator/denominator;
    return H;
}


void get_shapeindex_curvedness(double* p, vector<atom>& A, double sm, double& curv1, double& curv2)
{
    double grad[3];
    get_surf_grad_gorin(p, grad, A, sm);
  
    T3Matrix hess;
    get_surf_hess_gorin(p, hess, A, sm);

    double gc = get_gaussiancurv_value(grad, hess);
    double mc = get_meancurv_value(grad, hess);

    //curv1 = gc;
    //curv2 = mc;

    double kmax = mc + sqrt(mc*mc - gc);
    double kmin = mc - sqrt(mc*mc - gc);

    if(kmax >= kmin)
        curv1 = -1.* M_PI_INV2 * atan((kmax+kmin)/(kmax-kmin));
    if(curv1 > 1.)
        curv1 = 1.;
    if(curv1 < -1.)
        curv1 = -1.;
    //curv1 = -1. * atan((kmax+kmin)/(kmax-kmin));
    //curv2 = M_PI_INV2 * log(sqrt((kmax*kmax+kmin*kmin)/2.));
    curv2 = sqrt((kmax*kmax+kmin*kmin)/2.);
}



