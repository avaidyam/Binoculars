#ifndef _BASIS_H_
#define _BASIS_H_

typedef double Transform[4][4];

#include "pdb.h"


class basis
{
    public:
        Transform BASIS;
        double X_Y;
        int members[2]; // vector index of the points
        double THETA; // angle between normals
        // angles between segment and the normals
        double ALPHA; // reference axis angles
        double BETA; // reference axis angles
        double TAU; // torsion angle between planes
        double tfcor1[3], tfcor2[3]; // transformed coordinates of the RF
        double tfnrm1[3], tfnrm2[3]; // transformed normals of the RF

        basis(Transform M, double d, int* m, double t, double a1, double a2,
            double a3, double* g1, double* g2, double* c1, double* c2)
        {
            for(int i=0;i<4;i++)
                for(int j=0;j<4;j++)
                    BASIS[i][j] = M[i][j];
            for(int i=0;i<2;i++)
                members[i] = m[i];
            X_Y = d;
            ALPHA = a1;
            BETA = a2;
            TAU = a3;
            THETA = t;
            for(int i=0;i<3;i++)
            {
                tfcor1[i] = g1[i];
                tfcor2[i] = g2[i];
                tfnrm1[i] = c1[i];
                tfnrm2[i] = c2[i];
            }
        }
        basis(){}
        ~basis(){}
};

#endif  /* _BASIS_H_ */
