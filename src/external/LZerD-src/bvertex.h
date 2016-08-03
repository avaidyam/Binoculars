#ifndef _BVERTEX_H_
#define _BVERTEX_H_

// basis vertex
class bvertex
{
public:
    int cpid; // the critical point id transformed
    double coords[3];
    double nrm[3];
    double tau1, tau2;// torsion angles
    double gamma, delta;// pairwise normal angles
	double dist1, dist2;// distances to reference points


    bvertex(){};
    bvertex(double* b, double* c, int id, double t1, double t2, double g1, double g2, double d1, double d2)
    {
        for(int i=0;i<3;i++)
		{
            coords[i] = b[i];
            nrm[i] = c[i];
    	}
        cpid = id;
        tau1 = t1;
        tau2 = t2;
		gamma = g1;
		delta = g2;
		dist1 = d1;
		dist2 = d2;
    }
    ~bvertex(){};
};

#endif
