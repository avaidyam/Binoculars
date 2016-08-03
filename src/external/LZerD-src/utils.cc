#include "utils.h"
#include "MersenneTwister.h"
#include "constants.h"

#include <algorithm>
#include <fstream>

/*
template <class T>
inline string to_string (const T& t)
{
    stringstream ss;
    ss << t;
    return ss.str();
}
*/

// Returns false if at some point a critical point was faulty
bool check_cp(vector<cp>& M)
{
    vector<cp> N;
    bool success = true;

    for(size_t i=0;i<M.size();i++)
    {
        bool found = false;
	size_t zero_coefficient_count = 0;
        vector<double> Z = M[i].ZINV;
        for(size_t j=0;j<Z.size();j++)
        {
            if(isnan(Z[j]) || isinf(Z[j]))
            {
	    	cerr << "Warning: Critical point #" << i << ", coefficient #" << j << " is nan or infinity\n";
                found = true;
		success = false;
                break;
            }
	    if(Z[j] == 0)
	    {
		zero_coefficient_count++;
	    }
        }
	if(zero_coefficient_count == Z.size()) // namely, all zeroes is not good
	{
	    cerr << "Warning: Critical point #" << i << " contains all zeroes, nan or infinity\n";
            found = true;
	    success = false;
	}

        if(!found)
            N.push_back(M[i]);
    }

    M.clear();
    M = N;

    return success;
}

string int_to_string(int t)
{
    stringstream ss;
    ss << t;
    return ss.str();
}



void invert_normals(vector<cp>& L)
{
    for(size_t i=0;i<L.size();i++)
    {
        for(int j=0;j<3;j++)
            L[i].nrm[j] *= -1;
    }
}

void normalize(double* p, int k)
{
    double z = 0.;
    for(int i=0;i<k;i++)
    {
        z+= p[i]*p[i];
    }
    z = sqrt(z);
    for(int i=0;i<k;i++)
    {
        p[i] /= z;
    }
}

void point_transform (double* p, Transform m)
{
    double x, y, z;
    x = m[0][0]*p[0] + m[0][1]*p[1] + m[0][2]*p[2] + m[0][3];
    y = m[1][0]*p[0] + m[1][1]*p[1] + m[1][2]*p[2] + m[1][3];
    z = m[2][0]*p[0] + m[2][1]*p[1] + m[2][2]*p[2] + m[2][3];
    p[0] = x; p[1] = y; p[2] = z;
}

void normal_transform (double* p, Transform m)
{
    double x, y, z;
    x = m[0][0]*p[0] + m[0][1]*p[1] + m[0][2]*p[2];
    y = m[1][0]*p[0] + m[1][1]*p[1] + m[1][2]*p[2];
    z = m[2][0]*p[0] + m[2][1]*p[1] + m[2][2]*p[2];
    p[0] = x; p[1] = y; p[2] = z;
}

void get_cog(vector<atom>& C, double* cog)
{
    for(size_t i=0;i<C.size();i++)
    {
        for(int j=0;j<3;j++)
           cog[j] += C[i].axyz[j];
    }
    for(int i=0;i<3;i++)
       cog[i] /= (double) C.size();
}

void translate_cp(vector<cp>& C, double* cog)
{
    for(size_t i=0;i<C.size();i++)
    {
        for(int j=0;j<3;j++)
        {
            C[i].xyz[j] += cog[j];
        }
    }
}

void translate_atoms(vector<atom>& C, double* cog)
{
    for(size_t i=0;i<C.size();i++)
    {
        for(int j=0;j<3;j++)
        {
            C[i].axyz[j] += cog[j];
        }
    }
}


// http://mathworld.wolfram.com/EulerAngles.html
void rotate_coord(double oldX, double oldY, double oldZ,
                 double& newX, double& newY, double& newZ,
                 double psi, double theta, double phi)
{
    double r11, r21, r31, r12, r22, r32, r13, r23, r33;

    r11 = cos(psi)*cos(phi)-cos(theta)*sin(phi)*sin(psi);
    r12 = cos(psi)*sin(phi) + cos(theta)*cos(phi)*sin(psi);
    r13 = sin(psi)*sin(theta);
    r21 = -sin(psi)*cos(phi) - cos(theta)*sin(phi)*cos(psi);
    r22 = -sin(psi)*sin(phi) + cos(theta)*cos(phi)*cos(psi);
    r23 = cos(psi)*sin(theta);
    r31 = sin(theta)*sin(phi);
    r32 = -sin(theta)*cos(phi);
    r33 = cos(theta);

    newX = r11 * oldX + r12 * oldY + r13 * oldZ;
    newY = r21 * oldX + r22 * oldY + r23 * oldZ;
    newZ = r31 * oldX + r32 * oldY + r33 * oldZ;
}


//    * α and γ range from 0 to 2π radians.
//    * β ranges from 0 to π radians.
void apply_random_rotation(vector<atom>& A, vector<cp>& C)
{
    double cog[3];
    cog[0] = 0.; cog[1] = 0.; cog[2] = 0.;
    get_cog(A, cog);
    cog[0] *= -1.; cog[1] *= -1.; cog[2] *= -1.;
    translate_atoms(A, cog);
    translate_cp(C, cog);

    double phi = 0., theta = 0., psi = 0.;

    double tx1, ty1, tz1, oldx, oldy, oldz;
    MTRand objrnd;
    phi = objrnd.rand(M_PI); theta = objrnd.rand(M_PI); psi = objrnd.rand(M_PI);

    vector<atom> NB;
    for(size_t i=0;i<A.size();i++)
    {
        atom RA = A[i];
        oldx = RA.axyz[0];
        oldy = RA.axyz[1];
        oldz = RA.axyz[2];

        rotate_coord(oldx, oldy, oldz, tx1, ty1, tz1, psi, theta, phi);
        RA.axyz[0] = tx1;RA.axyz[1] = ty1;RA.axyz[2] = tz1;

        NB.push_back(RA);
    }
    A.clear();
    A = NB;


    vector<cp> NC;
    double ntx1, nty1, ntz1, oldnx, oldny, oldnz;
    for(size_t i=0;i<C.size();i++)
    {
        cp RA = C[i];
        oldx = RA.xyz[0];
        oldy = RA.xyz[1];
        oldz = RA.xyz[2];

        oldnx = RA.nrm[0];
        oldny = RA.nrm[1];
        oldnz = RA.nrm[2];

        rotate_coord(oldx, oldy, oldz, tx1, ty1, tz1, psi, theta, phi);
        rotate_coord(oldnx, oldny, oldnz, ntx1, nty1, ntz1, psi, theta, phi);

        RA.xyz[0] = tx1;RA.xyz[1] = ty1;RA.xyz[2] = tz1;
        RA.nrm[0] = ntx1; RA.nrm[1] = nty1; RA.nrm[2] = ntz1;

        NC.push_back(RA);
    }
    C.clear();
    C = NC;
 
    cout << "LIG: " << phi << " " << theta << " " << psi << " " << cog[0]
         << " " << cog[1] << " " << cog[2] << endl;
}


vector<pair<string, string> > get_residues_in_range(double* f, vector<atom>& A, ANNkd_tree* T)
{
    ANNidxArray nnIdx = NULL;
    ANNdistArray dists = NULL;
    ANNpoint query = annAllocPt(3);
    double eps = 0.;
    double r2 = 36.;

    vector<pair<string, string> > V;
            
    for (int j=0;j<3;j++)
        query[j] = f[j];
    int nnode = T->annkFRSearch(query, r2, 0, nnIdx, dists, eps); // search for n of them.
    if(nnode <= 0)
        return V;

    nnIdx = new ANNidx[nnode];
    dists = new ANNdist[nnode];
    T->annkFRSearch(query, r2, nnode, nnIdx, dists, eps); // search for n of them.

    set<pair<string, string> > X;

    int id = -1;
    for(int p1=0;p1<nnode;p1++)
    {
        id =  nnIdx[p1];
        if(A[id].atype == "CA" || A[id].atype == "CB")
        {
            // add to list
            pair<string, string> P(A[id].residue + int_to_string(A[id].rnum), A[id].chain);
            X.insert(P);
        }            
    }


    delete [] nnIdx;
    delete [] dists;

    if(X.size() > 0)
    {
        set<pair<string, string> >::iterator it;
        for(it = X.begin();it!=X.end();++it)
            V.push_back(*it);
    }

    return V;
}



void filter_cp(vector<cp>& C, vector<atom>& A, vector<pair<string, string> >& RESIDS_CHAINIDS)
{
    // identify list of residues for each cp in the 6A range;
    // include only if CA/CB atoms are found
    int N = (int) A.size();
    ANNpointArray dataPts;
    dataPts = annAllocPts(N, 3);
    for(int j=0; j<N; j++)
        for(int k=0;k<3;k++)
            dataPts[j][k] = A[j].axyz[k];
    ANNkd_tree *T = new ANNkd_tree(dataPts, N, 3);

    vector<pair<string, string> >::iterator it;

    // for each point maintain a vector of residues
    vector<cp> NC;
    for(size_t i=0;i<C.size();i++)
    {
        vector<pair<string, string> > V = get_residues_in_range(C[i].xyz, A, T);
        if(V.size() > 0)
        {
            for(size_t j=0;j<RESIDS_CHAINIDS.size();j++)
            {
                it = find(V.begin(), V.end(), RESIDS_CHAINIDS[j]);
                if(it != V.end())
                     NC.push_back(C[i]);
            }
        }
    }
    delete T;
    annDeallocPts(dataPts);
    annClose();

    C.clear();
    C = NC;
    cerr << "debug: Points after interface filtering " << C.size() << endl;
}

double get_norm(double* N, int k)
{
    double z = 0.;
    for(int i=0;i<k;i++)
    {
        z += N[i]*N[i];
    }
    return sqrt(z);
}


double compute_angle(double* n1, double* n2)
{
    double a = 0.;
    for(int i=0;i<3;i++)
    {
       a += n1[i]*n2[i];
    }
    if(isnan(a) || isinf(a))
    {
        return 0.;
    }
    if (a > 1.0) a = 1.0;
    if (a < -1.0) a = -1.0;
    return acos(a);
}

double compute_dotp(double* n1, double* n2)
{
    double v1 = get_norm(n1, 3);
    double v2 = get_norm(n2, 3);
    if(v1 == 0. || v2 == 0.)
        return 0.;
    double a = 0.;
    for(int i=0;i<3;i++)
    {
        a += n1[i]*n2[i];
    }
    a /= (v1*v2);
    if (a > 1.0) a = 1.0;
    if (a < -1.0) a = -1.0;
    return a;
}



double compute_angle(vector<double>& n1, vector<double>& n2)
{
    double a = 0.;
    for(int i=0;i<3;i++)
    {
       a += n1[i]*n2[i];
    }
    if (a > 1.0) a = 1.0;
    if (a < -1.0) a = -1.0;
    return acos(a);
}


/**
 * distance:
 * @p: point 1
 * @q: point 2
 * Calculate euclidean distance between points.
 */

double get_distance(double *p, double *q)
{
    double f0 = p[0] - q[0];
    double f1 = p[1] - q[1];
    double f2 = p[2] - q[2];
    //cerr << "distances: " << f0 << " " << f1 << " " << f2  << endl;
    
    f0 *= f0;f1 *= f1; f2 *= f2;
    double d = sqrt(f0 + f1 + f2);
    return d;
}

double get_distance2(double *p, double *q)
{
    double f0 = p[0] - q[0];
    double f1 = p[1] - q[1];
    double f2 = p[2] - q[2];
    f0 *= f0;f1 *= f1; f2 *= f2;
    double d = f0 + f1 + f2;
    return d;
}


// a × b = (a2b3 − a3b2) i + (a3b1 − a1b3) j + (a1b2 − a2b1) k = (a2b3 − a3b2, a3b1 − a1b3, a1b2 − a2b1
void cross_prod(double* a, double* b, double* f)
{
    f[0] = a[1]*b[2] - a[2]*b[1];
    f[1] = a[2]*b[0] - a[0]*b[2];
    f[2] = a[0]*b[1] - a[1]*b[0];
}

double get_torsion_angle(double* r1, double* n1, double* r2, double* n2)
{
    double f1[3];
    cross_prod(r1, n1, f1);

    double f2[3];
    cross_prod(r2, n2, f2);

    normalize(f1, 3);  normalize(f2, 3);

    double z = 0.;
    for(int i=0;i<3;i++)
        z += f1[i]*f2[i];

    if (z > 1.0) z = 1.0;
    if (z < -1.0) z = -1.0;

    z = acos(z);

    double S[3];
    cross_prod(f1, f2, S);

    if((S[0] * r1[0] + S[1] * r1[1] + S[2] * r1[2]) > 0.)
        z = -z;

    z = (z > 0.0) ? M_PI-z : -(M_PI+z);

    return z;
    
}


