#include "gh.h"

/*
 * double = det2x2( double a, double b, double c, double d )
 * 
 * calculate the determinant of a 2x2 matrix.
 */

double det2x2(double a, double b, double c, double d)
{
    double ans;
    ans = a * d - b * c;
    return ans;
}

/*
 * double = det3x3(  a1, a2, a3, b1, b2, b3, c1, c2, c3 )
 * 
 * calculate the determinant of a 3x3 matrix
 * in the form
 *
 *     | a1,  b1,  c1 |
 *     | a2,  b2,  c2 |
 *     | a3,  b3,  c3 |
 */

double det3x3(double a1, double a2, double a3, double b1, double b2,
    double b3, double c1, double c2, double c3)
{
    double ans;

    ans = a1 * det2x2(b2, b3, c2, c3)
        - b1 * det2x2(a2, a3, c2, c3)
        + c1 * det2x2(a2, a3, b2, b3);
    return ans;
}


/**
 * gts_matrix3_inverse:
 * @m: a 3x3 #GtsMatrix.
 *
 * Returns: a pointer to a newly created 3x3 #GtsMatrix inverse of @m or %NULL
 * if @m is not invertible.
 */
void matrix3_inverse(T3Matrix m, T3Matrix mi)
{
    double det;
    det = (m[0][0]*(m[1][1]*m[2][2] - m[2][1]*m[1][2]) -
        m[0][1]*(m[1][0]*m[2][2] - m[2][0]*m[1][2]) + 
        m[0][2]*(m[1][0]*m[2][1] - m[2][0]*m[1][1]));
    if(det == 0.0)
    {
        cerr << "Matrix determinant 0" << endl;
        exit(EXIT_FAILURE);
    }

    mi[0][0] = (m[1][1]*m[2][2] - m[1][2]*m[2][1])/det; 
    mi[0][1] = (m[2][1]*m[0][2] - m[0][1]*m[2][2])/det;
    mi[0][2] = (m[0][1]*m[1][2] - m[1][1]*m[0][2])/det; 
    mi[1][0] = (m[1][2]*m[2][0] - m[1][0]*m[2][2])/det; 
    mi[1][1] = (m[0][0]*m[2][2] - m[2][0]*m[0][2])/det; 
    mi[1][2] = (m[1][0]*m[0][2] - m[0][0]*m[1][2])/det; 
    mi[2][0] = (m[1][0]*m[2][1] - m[2][0]*m[1][1])/det; 
    mi[2][1] = (m[2][0]*m[0][1] - m[0][0]*m[2][1])/det; 
    mi[2][2] = (m[0][0]*m[1][1] - m[0][1]*m[1][0])/det; 

    return;
}

// this is actually the inverse
void matrix_inverse(Transform m, Transform mi, double* O)
{
    for(int i=0;i<3; i++)
    {
        for(int j=0;j<3; j++)
        {
            mi[i][j] = m[j][i];
        }
    }
    mi[0][3] = O[0]; mi[1][3] = O[1]; mi[2][3] = O[2];
 
    // include the reverse transformation of the origin
    // translation
    mi[3][0] = 0.; mi[3][1] = 0.;
    mi[3][2] = 0.;mi[3][3] = 1.;
}
// 4x4 matrix [R|t] is the mixture of 3x3 rotation matrix R and translation 3D vector t. Let's call [R|t] transformation matrix.
// Multiplying transformation matrix and transformation matrix results in transformation matrix
void matrix_prod(Transform a, Transform b, Transform c)
{
    int i, j, k;
    for(i=0;i<4; i++)
    {
        for(j=0;j<4; j++)
        {
            c[i][j] = 0.;
        }
    }	
    
    for(i=0;i<4; i++)
    {
        for(j=0;j<4; j++)
        {
            for(k=0;k<4; k++)
            {
                c[i][j] += (a[i][k] * b[k][j]);
            }
        }
    }
}




