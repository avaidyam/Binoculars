#include "gh.h"
#include <cfloat>
#include <iomanip>
#include "ptalign.h"
#include "utils.h"
#include <limits>

double CUTOFF_ANGLE = 1.;

bool find_point(vector<int>& V, int k)
{
    if(V.size() == 0) return false;
    vector<int>::iterator pos;
    pos = find(V.begin(), V.end(), k);
    if(pos == V.end())
        return false;
    return true;
}


void point_transform (vector<vector<double> >& p, Transform m, int i)
{
    double x, y, z;
    x = m[0][0]*p[i][0] + m[0][1]*p[i][1] + m[0][2]*p[i][2] + m[0][3];
    y = m[1][0]*p[i][0] + m[1][1]*p[i][1] + m[1][2]*p[i][2] + m[1][3];
    z = m[2][0]*p[i][0] + m[2][1]*p[i][1] + m[2][2]*p[i][2] + m[2][3];
    p[i][0] = x; p[i][1] = y; p[i][2] = z;
}

void normal_transform (vector<vector<double> >& p, Transform m, int i)
{
    double x, y, z;
    x = m[0][0]*p[i][0] + m[0][1]*p[i][1] + m[0][2]*p[i][2];
    y = m[1][0]*p[i][0] + m[1][1]*p[i][1] + m[1][2]*p[i][2];
    z = m[2][0]*p[i][0] + m[2][1]*p[i][1] + m[2][2]*p[i][2];
    p[i][0] = x; p[i][1] = y; p[i][2] = z;
}

void transform_points(Transform M, vector<cp>& X, vector<cp>& Y)
{
    for(size_t i=0;i<X.size();i++)
    {
        cp A = X[i];
        point_transform(A.xyz, M);
        normal_transform(A.nrm, M);
        Y.push_back(A);
    }
}

// get the original indexes of the cps 
void get_original_cps(basis& VB_R, basis& VB_L, 
    vector<bvertex>& Q, vector<bvertex>& P, vector<int>& XC, vector<int>& YC, 
    vector<int>& R, vector<int>& L)
{
    for(size_t i=0;i<XC.size();i++)
    {
        R.push_back(Q[XC[i]].cpid);
        L.push_back(P[YC[i]].cpid);
    }
    R.push_back(VB_R.members[0]);R.push_back(VB_R.members[1]);
    L.push_back(VB_L.members[0]);L.push_back(VB_L.members[1]);
}

bool verify_transformation(vector<bvertex>& Q, vector<bvertex>& P, vector<int>& XC, vector<int>& YC, 
    basis& VB_R, basis& VB_L, vector<vector<double> >& A, vector<vector<double> >& B,
    vector<vector<double> >& C, vector<vector<double> >& D,
    vector<cp>& REC, vector<cp>& LIG, Transform T)
{
    vector<cp> VNL;
    transform_points(T, LIG, VNL);


    //for(size_t i=0;i<XC.size();i++)
    //    cerr << "XC: " << XC[i] << " YC: " << YC[i] << endl;
    
    //cerr << "Press a key to continue ...";
    //getchar();
    
    // get original cps
    vector<int> R, L;
    get_original_cps(VB_R, VB_L, Q, P, XC, YC, R, L);

    int k = 0;
    double d, a;


    if(R.size() != L.size())
    {
        cerr << "Transformation verification: Error: sizes of vectors do not match" << endl;
        exit(EXIT_FAILURE);
    }

    for(size_t i=0;i<R.size();i++)
    {
        d = get_distance2(REC[R[i]].xyz, VNL[L[i]].xyz);
        if(d > 4.) continue;
        a = compute_angle(REC[R[i]].nrm, VNL[L[i]].nrm);
        if(a <= CUTOFF_ANGLE)
            k++;
    }

    if(k <= 0)
        return false;
    return true;
}

bool verify_alignment(vector<bvertex>& Q, vector<bvertex>& P, vector<int>& XC, vector<int>& YC, 
    basis& VB_R, basis& VB_L, vector<vector<double> >& A, vector<vector<double> >& B,
    vector<vector<double> >& C, vector<vector<double> >& D,
    vector<cp>& REC, vector<cp>& LIG, Transform T, int n)
{
    vector<cp> VNL;
    transform_points(T, LIG, VNL);

    if(XC.size() != YC.size())
    {
        cerr << "Alignment verification: Error: sizes of vectors do not match. XC != YC" << endl;
        exit(EXIT_FAILURE);
    }

    // get original cps
    vector<int> R, L;
    for(size_t i=0;i<XC.size();i++)
    {
        R.push_back(Q[XC[i]].cpid);
        L.push_back(P[YC[i]].cpid);
    }


    if((R.size() != XC.size()) || (L.size() != YC.size()))
    {
        //cerr << "R: " << R.size() << ": " << XC.size() << " L: " << L.size() << ": " << YC.size() << endl;
        cerr << "Alignment verification: Error: sizes of vectors do not match. R,XC" << endl;
        exit(EXIT_FAILURE);
    }

    //for(size_t i=0;i<R.size();i++)
    //    cerr << "R: " << R[i] << " L: " << L[i] << endl;


    vector<vector<double> > MA, MB, MC, MD;
    vector<int> NXC, NYC;

    if(R.size() != L.size())
    {
        cerr << "Alignment verification: Error: sizes of vectors do not match" << endl;
        exit(EXIT_FAILURE);
    }

    double d=0., a=0.;

    int k = 0;
    for(size_t i=0;i<R.size();i++, k++)
    {
        d = get_distance2(REC[R[i]].xyz, VNL[L[i]].xyz);
        if(d > 4.)
            continue;
        a = compute_angle(REC[R[i]].nrm, VNL[L[i]].nrm);
        if(a > CUTOFF_ANGLE)
            continue;
        MA.push_back(A[i]);
        MB.push_back(B[i]);
        MC.push_back(C[i]);
        MD.push_back(D[i]);
        //cerr << "Adding NXC: " << XC[i] << " NYC: " << YC[i] << endl;
        NXC.push_back(XC[i]);
        NYC.push_back(YC[i]);
    }

    R.push_back(VB_R.members[0]);R.push_back(VB_R.members[1]);
    L.push_back(VB_L.members[0]);L.push_back(VB_L.members[1]);

    // do the same for the points that were used for the basis
    // for the first pair of points in the basis 
    d = get_distance2(REC[VB_R.members[0]].xyz, VNL[VB_L.members[0]].xyz);
    a = compute_angle(REC[VB_R.members[0]].nrm, VNL[VB_L.members[0]].nrm);
    if(d <= 4. && a <= CUTOFF_ANGLE)
    {
        MA.push_back(A[k]);
        MB.push_back(B[k]);
        MC.push_back(C[k]);
        MD.push_back(D[k]);
    }
    k++;
    // for the second pair of points in the basis
    d = get_distance2(REC[VB_R.members[1]].xyz, VNL[VB_L.members[1]].xyz);
    a = compute_angle(REC[VB_R.members[1]].nrm, VNL[VB_L.members[1]].nrm);
    if(d <= 4. && a <= CUTOFF_ANGLE)
    {
        MA.push_back(A[k]);
        MB.push_back(B[k]);
        MC.push_back(C[k]);
        MD.push_back(D[k]);
    }
    

    if((int)MA.size() < n)
    {
        return false;
    }
    if(A.size() == MA.size())
    {
        return false;
    }
    A.clear();B.clear();C.clear();D.clear();

    //for(size_t i=0;i<NXC.size();i++)
    //    cerr << "NXC: " << NXC[i] << " NYC: " << NYC[i] << endl;


    A = MA; B = MB; C = MC; D = MD;
    XC.clear(); YC.clear();
    XC = NXC; YC = NYC;

    return true;
}

