#include "gh.h"
#include "ptalign.h"
#include <iomanip>
#include <fstream>

#ifdef WITH_MPI

// only include if the mpi version is being compiled
#include "mpi.h"

#endif

const double EXVOL_CUTOFF = 200.;
const double BSA_CUTOFF = 650.;

const double WEIGHTS[4] = {-1.6060, 0.3840, -26.3021, -10.1119};
const double CTRVAL[4] = {-258.4987, 851.2984, -21.8962, 52.5770};

// Global output stream that can be either cout if no particular filename was provided
// or a specific file based on the filename passed to dock
ostream* output_stream;


bool check_reference_angles(double a, double b)
{
    if(fabs(a-b) > REFERENCE_AXIS_ANGLE_CONSTRAINT)
        return false;
    if(a < MIN_REFERENCE_AXIS_ANGLE_CONSTRAINT || a > MAX_REFERENCE_AXIS_ANGLE_CONSTRAINT)
        return false;
    if(b < MIN_REFERENCE_AXIS_ANGLE_CONSTRAINT || b > MAX_REFERENCE_AXIS_ANGLE_CONSTRAINT)
        return false;
    return true;
}

double get_weighted_score(double exvol, double sasa, double reward, double penalty)
{
    double f =  WEIGHTS[0] * fabs(exvol - CTRVAL[0]) +
                WEIGHTS[1] * fabs(sasa - CTRVAL[1]) +
                WEIGHTS[2] * fabs(reward - CTRVAL[2]) +
                WEIGHTS[3] * fabs(penalty - CTRVAL[3]);
    return f;
}



bool check_members(int j, int* members)
{
    for(int i=0;i<2;i++)
        if(j == members[i])
            return false;
    return true;
}

vector<bvertex> get_transformed_coordinates(basis& B, vector<cp>& G, double rfdist)
{
    double d1 = 0., d2 = 0.;
    double t1 = 0., t2 = 0., g1 = 0., g2 = 0.;
    int p1 = 0, p2 = 0;
    double r1[3], r2[3];

	vector<bvertex> P;
	for(size_t j=0;j<G.size();j++) // loop thru vertex list
    {
        // check if the index of the CP has been used in the formation of the basis
        if(check_members(j, B.members))
        {
            p1 = B.members[0];
            p2 = B.members[1];
            d1 = get_distance(G[p1].xyz, G[j].xyz);
            if(d1 > rfdist)
                continue;
            d2 = get_distance(G[p2].xyz, G[j].xyz);
            if(d2 > rfdist)
                continue;

            // transform coordinates
            double point[3], pnrm[3];
            for(int k=0;k<3;k++)
			{
                point[k] = G[j].xyz[k];
                pnrm[k] = G[j].nrm[k];
			}
            point_transform(point, B.BASIS);
            normal_transform(pnrm, B.BASIS);

            for(int k=0;k<3;k++)
            {
                r1[k] = point[k] - B.tfcor1[k];
                r2[k] = -r1[k];
            }
            normalize(r1, 3);normalize(r2, 3);
            t1 = get_torsion_angle(r1, pnrm, r2, B.tfnrm1);
            for(int k=0;k<3;k++)
            {
                r1[k] = point[k] - B.tfcor2[k];
                r2[k] = -r1[k];
            }
            normalize(r1, 3);normalize(r2, 3);
            t2 = get_torsion_angle(r1, pnrm, r2, B.tfnrm2);

            // calculate pairwise normal angles
            g1 = compute_angle(pnrm, B.tfnrm1);
            g2 = compute_angle(pnrm, B.tfnrm2);

            d1 = get_distance(point, B.tfcor1);
            d2 = get_distance(point, B.tfcor2);

            // for each basis-point pair, store the distances to the point from the 2 ref coordinates
            bvertex nb(point, pnrm, (int)j, t1, t2, g1, g2, d1, d2);
            P.push_back(nb);
        } // end if	
    }// end for	
	return P;
}

// compute coordinates of the remaining features using the coordinate frames defined in VB
void transform_coordinates(vector<cp>& G, vector<basis>& VB, map<int, vector<bvertex> >& M,
    double rfdist)
{
    double d1 = 0., d2 = 0.;
    double t1 = 0., t2 = 0., g1 = 0., g2 = 0.;
    int p1 = 0, p2 = 0;
    double r1[3], r2[3];

    for(size_t i=0;i<VB.size();i++) // for number of basis created
    {
		vector<bvertex> P;
        for(size_t j=0;j<G.size();j++) // loop thru vertex list
        {
            // check if the index of the CP has been used in the formation of the basis
            if(check_members(j, VB[i].members))
            {
                p1 = VB[i].members[0];
                p2 = VB[i].members[1];
                d1 = get_distance(G[p1].xyz, G[j].xyz);
                if(d1 > rfdist)
                    continue;
                d2 = get_distance(G[p2].xyz, G[j].xyz);
                if(d2 > rfdist)
                    continue;

                // transform coordinates
                double point[3], pnrm[3];
                for(int k=0;k<3;k++)
				{
                    point[k] = G[j].xyz[k];
                    pnrm[k] = G[j].nrm[k];
			    }
                point_transform(point, VB[i].BASIS);
                normal_transform(pnrm, VB[i].BASIS);

                for(int k=0;k<3;k++)
                {
                    r1[k] = point[k] - VB[i].tfcor1[k];
                    r2[k] = -r1[k];
                }
                normalize(r1, 3);normalize(r2, 3);
                t1 = get_torsion_angle(r1, pnrm, r2, VB[i].tfnrm1);
                for(int k=0;k<3;k++)
                {
                    r1[k] = point[k] - VB[i].tfcor2[k];
                    r2[k] = -r1[k];
                }
                normalize(r1, 3);normalize(r2, 3);
                t2 = get_torsion_angle(r1, pnrm, r2, VB[i].tfnrm2);

                // calculate pairwise normal angles
                g1 = compute_angle(pnrm, VB[i].tfnrm1);
                g2 = compute_angle(pnrm, VB[i].tfnrm2);

                d1 = get_distance(point, VB[i].tfcor1);
                d2 = get_distance(point, VB[i].tfcor2);

                // for each basis-point pair, store the distances to the point from the 2 ref coordinates
                bvertex nb(point, pnrm, (int)j, t1, t2, g1, g2, d1, d2);
                P.push_back(nb);
            } // end if			
        }// end for
		// add pair
		M.insert(make_pair((int)i, P));
    }// end for
}


// Then the change of basis from (X,Y,Z)-space to (U,V,N)-space (assuming that matrices
// multiply on the right of their operands) has U as its first row, V as its second row, and N as its
// third row. The inverse of this matrix is the transformation from (U,V,N)-space to (X,Y,Z)-space

void orthogonal_reference_frame(vector<Vertex>& P, Transform M)
{
    double X[3], Y[3], Z[3];
    int i;
    for(i=0;i<3;i++)
        X[i] = P[1][i] - P[0][i];
    normalize(X, 3);


    // y-axis
    // Compute vector  Y = ab x d  (cross product)
    // a × b = (a2b3 − a3b2) i + (a3b1 − a1b3) j + (a1b2 − a2b1) k = (a2b3 − a3b2, a3b1 − a1b3, a1b2 − a2b1)
    Y[0] = X[1]*P[2][2] - X[2]*P[2][1];
    Y[1] = X[2]*P[2][0] - X[0]*P[2][2];
    Y[2] = X[0]*P[2][1] - X[1]*P[2][0];
    normalize(Y, 3);

    // x * y -> z axis * implies cross
    Z[0] = X[1]*Y[2] - X[2]*Y[1];
    Z[1] = X[2]*Y[0] - X[0]*Y[2];
    Z[2] = X[0]*Y[1] - X[1]*Y[0];
    normalize(Z, 3);

    // row 1 , vector X
    // row 2 , vector Y
    // row 3 , vector Z
    M[0][0] = X[0];
    M[1][0] = Y[0];
    M[2][0] = Z[0];
	M[3][0] = 0.;
    M[0][1] = X[1];
    M[1][1] = Y[1];
    M[2][1] = Z[1];
	M[3][1] = 0.;
    M[0][2] = X[2];
    M[1][2] = Y[2];
    M[2][2] = Z[2];
	M[3][2] = 0.;

    double e = M[0][0]*P[0][0] + M[0][1]*P[0][1] + M[0][2]*P[0][2];
    double f = M[1][0]*P[0][0] + M[1][1]*P[0][1] + M[1][2]*P[0][2];
    double g = M[2][0]*P[0][0] + M[2][1]*P[0][1] + M[2][2]*P[0][2];

    M[0][3] = -e;
    M[1][3] = -f;
    M[2][3] = -g;
    M[3][3] = 1.;
}


void transform_atoms(Transform T, vector<atom>& P)
{
    vector<atom> Z;
    for(size_t i=0;i<P.size();i++)
    {
        atom q = P[i];
        point_transform(q.axyz, T);
        Z.push_back(q);
    }
    P.clear();
    P = Z;
}

void output_decoys(Transform T, double SCORE)
{
    (*output_stream) << fixed << left << setprecision(3);
    for(int i=0;i<3;i++)
    {
        for(int j=0;j<3;j++)
        {
            (*output_stream) << setw(6) << T[i][j] << "  ";
        }
    }
    (*output_stream) << setw(6) << T[0][3] << "  "
         << setw(6) << T[1][3] << "  "
         << setw(6) << T[2][3] << "  "
         << setw(6) << SCORE
         << endl;
}

// proposed transformation from rigid body superimposition
// receptor based superposition, with translation to origin
void get_transformation_matrix(double* T2L, Transform T1R, double* ro, Transform T1L, Transform T)
{
    // get the inverse of the R matrix
    Transform T1R_I;
    matrix_inverse(T1R, T1R_I, ro);

    // matrix rep for T2L
    Transform Z;
    int k = 0;
    for(int l=0;l<3;l++)
    {
        for(int j=0;j<3;j++)
        {
            Z[l][j] = T2L[k];
            k++;
        }
    }
    Z[0][3] = T2L[9];
    Z[1][3] = T2L[10];
    Z[2][3] = T2L[11];

    Z[3][0] = 0.;Z[3][1] = 0.;Z[3][2] = 0.;Z[3][3] = 1.;

    Transform F;
    matrix_prod(T1R_I, Z, F);

    matrix_prod(F, T1L, T);
}


void get_transformation_matrix(double* N, Transform T)
{
    // matrix rep for T2L
    Transform Z;
    int k = 0;
    for(int l=0;l<3;l++)
    {
        for(int j=0;j<3;j++)
        {
            Z[l][j] = N[k];
            k++;
        }
    }
    Z[0][3] = N[9];
    Z[1][3] = N[10];
    Z[2][3] = N[11];

    Z[3][0] = 0.;Z[3][1] = 0.;Z[3][2] = 0.;Z[3][3] = 1.;

    Transform F;
    matrix_prod(T, Z, F);

    // copy F to T
    for(int i=0;i<4; i++)
    {
        for(int j=0;j<4; j++)
        {
            T[i][j] = F[i][j];
        }
    }
}

// compute orthonormal basis for all triplets of non-collinear points
// store in a vector of basis.
void form_coordinate_basis(vector<cp>& G, vector<basis>& VB, double dmin, double dmax)
{
    double d = 0., theta = 0.;
    size_t vs = G.size();
    double alpha = 0., beta = 0., tau = 0.;
    double r1[3], r2[3], avnrm[3];;
    for(size_t i=0;i<vs-1;i++)
    {
        for(size_t j=i+1;j<vs;j++)
        {
            d = get_distance(G[i].xyz, G[j].xyz);

            // check distance constraints
            if(d >= dmin && d <= dmax)
            {
                // get average of normals
                for(int k=0;k<3;k++)
                    avnrm[k] = (G[i].nrm[k] + G[j].nrm[k])/2.;

                vector<Vertex> V;
                V.push_back(Vertex(G[i].xyz, 3));
                V.push_back(Vertex(G[j].xyz, 3));
                V.push_back(Vertex(avnrm, 3));
                Transform T;
                int m[2];
                m[0] = i;m[1] = j;
                orthogonal_reference_frame(V, T);

                double c1[3], c2[3], n1[3], n2[3];
                for(int k=0;k<3;k++)
                {
                    c1[k] = G[i].xyz[k];
                    n1[k] = G[i].nrm[k];
                    c2[k] = G[j].xyz[k];
                    n2[k] = G[j].nrm[k];
                }
                point_transform(c1, T);
                normal_transform(n1, T);
                point_transform(c2, T);
                normal_transform(n2, T);

                theta = compute_angle(n1, n2);
                if(theta > ANGLE_BETWEEN_NORMALS)
                    continue;

                // angle between each normal and the vector ab
                for(int l=0;l<3;l++)
                    r1[l] = c2[l] - c1[l];
                normalize(r1, 3);
                alpha = compute_angle(r1, n1);
                for(int l=0;l<3;l++)
                    r2[l] = r1[l]*-1;
                beta = compute_angle(r2, n2);
                if(!check_reference_angles(alpha, beta))
                    continue;

                tau = get_torsion_angle(r1, n1, r2, n2);
                if(fabs(tau) > 1.4)
                    continue;

                // record distances between the 2 points making the ref frame
                basis b(T, d, m, theta, alpha, beta, tau, c1, c2, n1, n2);
                VB.push_back(b);
            }
        }
    }
}



void update_vote_counter(vector<pair<int,int> >& VOTE_COUNTER, int RM, int LM)
{
    // check if one of the pair is present
    bool found = false;
    for(size_t i=0;i<VOTE_COUNTER.size();i++)
    {
	   pair<int,int> Y = VOTE_COUNTER[i];
       if(Y.first == RM || Y.second == LM)
       {
            found = true;
            break;
       }
    }
    if(!found)
        VOTE_COUNTER.push_back(make_pair(RM, LM));

}

void add_cps(basis& br, basis& bl, vector<vector<double> >& A,
    vector<vector<double> >& B, vector<vector<double> >& C, vector<vector<double> >& D)
{
    vector<double> a1(3), b1(3), c1(3), d1(3);
    for(int j=0;j<3;j++)
    {
        a1[j] = br.tfcor1[j];
        b1[j] = bl.tfcor1[j];
        c1[j] = br.tfnrm1[j];
        d1[j] = bl.tfnrm1[j];
    }
    A.push_back(a1); B.push_back(b1);
    C.push_back(c1); D.push_back(d1);

    for(int j=0;j<3;j++)
    {
        a1[j] = br.tfcor2[j];
        b1[j] = bl.tfcor2[j];
        c1[j] = br.tfnrm2[j];
        d1[j] = bl.tfnrm2[j];
    }
    A.push_back(a1); B.push_back(b1);
    C.push_back(c1); D.push_back(d1);
}


void get_coords(vector<bvertex>& R, vector<bvertex>& L, vector<int>& XC, vector<int>& YC,
	vector<vector<double> >& A, vector<vector<double> >& B,
    vector<vector<double> >& C, vector<vector<double> >& D)
{
    for(size_t i=0;i<XC.size();i++)
    {
        int t1 = XC[i];
        int t2 = YC[i];
        vector<double> a1(3), b1(3), c1(3), d1(3);
        for(int j=0;j<3;j++)
        {
            a1[j] = R[t1].coords[j];
            b1[j] = L[t2].coords[j];
            c1[j] = R[t1].nrm[j];
            d1[j] = L[t2].nrm[j];
        }
        A.push_back(a1); B.push_back(b1);// points
	    C.push_back(c1); D.push_back(d1);// normals
    }
}

// XC receptor
// YC ligand
// this only adds the pairs in the matching set of points
// does not add the points associated with the corresponding basis
void collect_cpids(vector<pair<int,int> >& M, vector<int>& XC, vector<int>& YC)
{
    for(size_t i=0;i<M.size();i++)
    {
        XC.push_back(M[i].first);
        YC.push_back(M[i].second);
    }
}


void calculate_transformation_matrix(vector<pair<int,int> >& VOTE_COUNTER,
    vector<bvertex>& CPL, vector<bvertex>& CPR, basis& VB_L,
    basis& VB_R, vector<cp>& LIG, vector<cp>& REC,
    vector<vector<double> >& MCP, vector<atom>& LIG_ATOMS,
    vector<atom>& REC_ATOMS, double LIG_SAS, double REC_SAS,
    ANNkd_tree* KTREC, ANNkd_tree* KTRATOM, int minvotes)
{
    vector<cp> TLIG;
	vector<atom> NALN;
    double exvol = 0., bsa = 0., reward = 0., penalty = 0.;
    double wts_score = 0.;
	double tform[12];

    //cerr << "PHASE I" << endl;

    // update XC and YC with the ids of the transformed CPs in each pair
    vector<int> XC, YC;//receptor, ligand
    collect_cpids(VOTE_COUNTER, XC, YC);
    if(XC.size() == 0 || YC.size() == 0)
    {
        cerr << "No points in list for transformation" << endl;
        exit(EXIT_FAILURE);
    }

    // populate vectors
    // add the coordinates of the transformed (matching) points
    vector<vector<double> > A, B, C, D;
    get_coords(CPR, CPL, XC, YC, A, B, C, D);

    // add the cp pairs
    // add the points forming the basis
    add_cps(VB_R, VB_L, A, B, C, D);

    Transform Z;
    // calculate a lsq-fit of the points
    if(!ptalign(A, B, tform))
        return;
    get_transformation_matrix(tform, VB_R.BASIS, REC[VB_R.members[0]].xyz, VB_L.BASIS, Z);
    if(!verify_transformation(CPR, CPL, XC, YC, VB_R, VB_L, A, B, C, D, REC, LIG, Z))
        return;
            
    reward = 0.; penalty = 0.;
    TLIG.clear();
    transform_points(Z, LIG, TLIG);
    get_score(TLIG, REC, MCP, KTREC, reward, penalty);
		
    // for the receptor-ligand combination
    // calculate surface overlap and clash penalty
    NALN = LIG_ATOMS;
    transform_atoms(Z, NALN);
    exvol = get_excluded_volume(NALN, REC_ATOMS, KTRATOM);
    if(exvol > EXVOL_CUTOFF || exvol < 5.)
        return;

    bsa = REC_SAS + LIG_SAS - get_sasa(NALN, REC_ATOMS);
    if(bsa < BSA_CUTOFF)
        return;

    // combine scores based on weights
    wts_score = get_weighted_score(exvol, bsa, reward, penalty);
    output_decoys(Z, wts_score);


    // transform cps of the seed match
    if(!verify_alignment(CPR, CPL, XC, YC, VB_R, VB_L, A, B, C, D, REC, LIG, Z, minvotes))
        return;

    if(!ptalign(A, B, tform))
        return;

    get_transformation_matrix(tform, VB_R.BASIS, REC[VB_R.members[0]].xyz, VB_L.BASIS, Z);
    if(!verify_transformation(CPR, CPL, XC, YC, VB_R, VB_L, A, B, C, D, REC, LIG, Z))
        return;

    reward = 0.; penalty = 0.;
    TLIG.clear();
    transform_points(Z, LIG, TLIG);
    get_score(TLIG, REC, MCP, KTREC, reward, penalty);

    // for the receptor-ligand combination
    // calculate surface overlap and clash penalty
    NALN = LIG_ATOMS;
    transform_atoms(Z, NALN);
    exvol = get_excluded_volume(NALN, REC_ATOMS, KTRATOM);
    if(exvol > EXVOL_CUTOFF || exvol < 5.)
        return;

    bsa = REC_SAS + LIG_SAS - get_sasa(NALN, REC_ATOMS);
    if(bsa < BSA_CUTOFF)
        return;

    // combine scores based on weights
    wts_score = get_weighted_score(exvol, bsa, reward, penalty);
    output_decoys(Z, wts_score);
}


bool distance_compatible(vector<bvertex>& P, vector<bvertex>& Q, int i, int j, double dcut)
{
    if(dcut == 0.)
        return true;
    double l12 = fabs(P[i].dist1 - Q[j].dist1);
    if(l12 > dcut)
        return false;
    double l13 = fabs(P[i].dist2 - Q[j].dist2);
    if(l13 > dcut)
        return false;
    return true;
}

bool normals_compatible(vector<bvertex>& P, vector<bvertex>& Q, int i, int j)
{
    double l12 = fabs(P[i].gamma - Q[j].gamma);
    if(l12 > PAIRWISE_NORMAL_ANGLE_CUTOFF)
        return false;
    double l13 = fabs(P[i].delta - Q[j].delta);
    if(l13 > PAIRWISE_NORMAL_ANGLE_CUTOFF)
        return false;
    return true;
}

bool torsion_angle_compatible(vector<bvertex>& P, vector<bvertex>& Q, int i, int j)
{
    double l12 = fabs(P[i].tau1 - Q[j].tau1);
    if(l12 > 0.7)
        return false;
    double l13 = fabs(P[i].tau2 - Q[j].tau2);
    if(l13 > 0.7)
        return false;
    return true;
}


// docking routine
// find transformations for non-collinear triplets of vertices
// V_S , prot_S -> ligand
// V_T, prot_T -> recptor
void dock(vector<cp>& REC, vector<atom>& REC_ATOMS, vector<cp>& LIG,
    vector<atom>& LIG_ATOMS, double corr_cutoff, double rfmin,
    double rfmax, double rfdist, double dcut, int votes, double nrad,
    string output_filename)
{
    // store critical point pairs with ZD correlation > cutoff
    cerr << "Calculating 3DZD correlations ..." << endl;
    int iL = (int) LIG.size();
    int iR = (int) REC.size();

    vector<vector<double> > MCP;
    for(int i=0;i<iL;i++)
    {
        vector<double> Z(iR, 0.);
        MCP.push_back(Z);
    }
    calculate_correlation_values(LIG, REC, MCP);
	
    // wee bit of cleanup
    for(size_t i=0;i<LIG.size();i++)
        LIG[i].ZINV.clear();

    for(size_t i=0;i<REC.size();i++)
        REC[i].ZINV.clear();

    cerr << "Creating basis for ligand ..." << endl;
    vector<basis> VB_L;
    form_coordinate_basis(LIG, VB_L, rfmin, rfmax);
    if(VB_L.size() == 0)
    {
        cerr << "Error: unable to form bases from the ligand coordinates" << endl;
        exit(EXIT_FAILURE);
    }

    // reference frames for the receptor
    cerr << "Creating basis for receptor ..." << endl;
    vector<basis> VB_R;
    form_coordinate_basis(REC, VB_R, rfmin, rfmax);
    if(VB_R.size() == 0)
    {
        cerr << "Error: unable to form bases from the receptor coordinates" << endl;
        exit(EXIT_FAILURE);
    }
    map<int, vector<bvertex> > TC_R;//transformed coordinates
    // transform all other coordinates (non members of the basis)
    transform_coordinates(REC, VB_R, TC_R, rfdist);

	
    cerr << "SASA calculation ..." << endl;
    double LIG_SAS, REC_SAS;
    LIG_SAS = calculate_sas(LIG_ATOMS);

    REC_SAS = calculate_sas(REC_ATOMS);
	
    // add cp's of Receptor
    ANNpointArray RECdataPts;
    int dim = 3;
    RECdataPts = annAllocPts(iR, dim);

    for(int i=0;i<iR;i++)
    {
        for(int j=0;j<dim;j++)
            RECdataPts[i][j] = REC[i].xyz[j];
    }
    ANNkd_tree *KTREC = new ANNkd_tree(RECdataPts, iR, dim);
	
    ANNpoint query = annAllocPt(dim);
    double eps = 0.;
    double r2 = nrad*nrad;

    // kd tree for receptor atoms
    int N = (int) REC_ATOMS.size();
    ANNpointArray RECATOMdataPts;
    RECATOMdataPts = annAllocPts(N, dim);
    for(int j=0; j<N; j++)
        for(int k=0;k<dim;k++)
            RECATOMdataPts[j][k] = REC_ATOMS[j].axyz[k];
    ANNkd_tree *KTRATOM = new ANNkd_tree(RECATOMdataPts, N, dim);

    int a = 0, b = 0, d = 0, e = 0;
    double d1 = 0., d2 = 0., d3 = 0., dt = 0.;

    cerr << "Voting ..." << endl;

#ifdef WITH_MPI
    // If mpi is used the different bases will be distributed between
    // different ranks
    int rank, numtasks;
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &numtasks);
#endif

    // Before starting the actual process, set the output stream to the appropriate
    // value
    if(output_filename.empty())
    {
        output_stream = &cout;
    }
    else
    {

#ifdef WITH_MPI
	char rank_number_str[4];
	sprintf(rank_number_str, "%03i", rank);
	output_stream = new ofstream((rank_number_str + string("-") +
					output_filename).c_str(), ios_base::out);
#else
	output_stream = new ofstream(output_filename.c_str(), ios_base::out);
#endif
    }

    // compare L-basis with R-basis
    // for every basis in LIG
#ifdef WITH_MPI
    int total_size = VB_L.size();
    int iterations_per_task = total_size / numtasks;
    int total_remainder = total_size % numtasks;
    int remainder_processed = total_remainder > rank ? rank : total_remainder;
    int start_index = iterations_per_task * rank + remainder_processed;
    int end_index = start_index + iterations_per_task;
    // the remainder will be given 1 by 1 to each of the first
    // ranks, until we run out of remaining bases
    if(rank < total_remainder)
    {
	end_index++;
    }
    size_t i = start_index;
    size_t loop_boundary = end_index;
#else
    size_t i = 0;
    size_t loop_boundary = VB_L.size();
#endif
    for(;i<loop_boundary;i++)
    {
        a = VB_L[i].members[0]; b= VB_L[i].members[1];
	    // get transformed coordinates of ligand for the current basis
        // transform coordinates of receptor points in this RF
        vector<bvertex> CPL = get_transformed_coordinates(VB_L[i], LIG, rfdist);
        if(CPL.size() == 0)
        {
            cerr << "Error: unable to transform ligand coordinates" << endl;
            exit(EXIT_FAILURE);
        }

	    // create Kd-tree of the transformed ligand coordinates
	    N = (int) CPL.size();
	    ANNpointArray dataPts;
	    dataPts = annAllocPts(N, dim);

	    for(int k=0;k<N;k++)
	    {
	        for(int l=0;l<dim;l++)
	        dataPts[k][l] = CPL[k].coords[l];
	    }
	    ANNbd_tree *T = new ANNbd_tree(dataPts, N, dim, 1, ANN_KD_SUGGEST, ANN_BD_NONE);

	    // for every basis in REC
        for(size_t j=0;j<VB_R.size();j++)
        {
            d = VB_R[j].members[0]; e= VB_R[j].members[1];
            // check zernike
	        if(MCP[a][d] < corr_cutoff)
	            continue;
            if(MCP[b][e] < corr_cutoff)
                continue;
            // check types
            // reject max-max, min-min
            if(LIG[a].type == 1 && REC[d].type == 1)
                continue;
            if(LIG[b].type == 2 && REC[e].type == 2)
                continue;


            // check pairwise normal angles
            dt = fabs(VB_L[i].THETA - VB_R[j].THETA);
            if(dt > PAIRWISE_NORMAL_ANGLE_CUTOFF)
                continue;

	        d1 = fabs(VB_L[i].ALPHA - VB_R[j].ALPHA);
            if(d1 > 0.6)
                continue;
			d2 = fabs(VB_L[i].BETA - VB_R[j].BETA);
            if(d2 > 0.6)
                continue;

			d3 = fabs(VB_L[i].TAU - VB_R[j].TAU);
            if(d3 > 0.7)
                continue;

            // from Norel et al
            if((d1 + d2) > REF_DIFF_ANGLE_TOL)
                continue;
            if((d1 + d2 + d3) > SUM_ANGLE_TOL)
                continue;

	        // distance check
            if(fabs(VB_L[i].X_Y - VB_R[j].X_Y) > dcut)
                continue;

            vector<bvertex> CPR = TC_R[j];

            // initialize VOTE counter
            vector<pair<int,int> > VOTE_COUNTER;
			
		    for(size_t i1=0;i1<CPR.size();i1++)
			{
				ANNidxArray nnIdx = NULL;
				ANNdistArray dists = NULL;
			
				for(int j1=0;j1<dim;j1++)
					query[j1] = CPR[i1].coords[j1];
				int nnode = T->annkFRSearch(query, r2, 0, nnIdx, dists, eps); // search for n of them.
				if(nnode <= 0)
					continue;

				nnIdx = new ANNidx[nnode];
				dists = new ANNdist[nnode];
				T->annkFRSearch(query, r2, nnode, nnIdx, dists, eps); // search for n of them.

                int rcp_id = CPR[i1].cpid;

				for(int p1=0; p1<nnode; p1++)
				{
					// access the bases lying in the node
					// if bases are compatible, increment vote counter
					int win_id = nnIdx[p1];
					if(win_id == ANN_NULL_IDX)
						break;

					int lcp_id = CPL[win_id].cpid;
					// now check if the ligand and receptor points are compatible
					// in terms of correlation, euclidean and the normals
                    if(MCP[lcp_id][rcp_id] > corr_cutoff)
                    {
                        if(LIG[lcp_id].type == 1 && REC[rcp_id].type == 1)
                            continue;
                        if(LIG[lcp_id].type == 2 && REC[rcp_id].type == 2)
                            continue;

						// now check the compatibility of the normals
						if(!normals_compatible(CPL, CPR, win_id, i1))
							continue;

						// now check distance compatibility
						if(!distance_compatible(CPL, CPR, win_id, i1, dcut))
							continue;

						// check torsion angles
						if(!torsion_angle_compatible(CPL, CPR, win_id, i1))
							continue;

						// YIPEE, now vote
                        // vote counter contains the index of the transformed critical point
                        // formed with respect to the current basis
						update_vote_counter(VOTE_COUNTER, i1, win_id);// receptor, ligand pair
					}
				}
		        delete [] nnIdx;
				delete [] dists;
			}
            // for the votes > threshold collected, find a transformation
            if(VOTE_COUNTER.size() > (size_t) votes)
            {
                calculate_transformation_matrix(VOTE_COUNTER, CPL, CPR, VB_L[i], VB_R[j], LIG, REC, MCP, LIG_ATOMS, REC_ATOMS, LIG_SAS, REC_SAS, KTREC, KTRATOM, votes);
            }
            VOTE_COUNTER.clear();
            CPR.clear();
		}
        CPL.clear();
        delete T;
        annDeallocPts(dataPts);
        annClose();
    }
    
    MCP.clear();
    TC_R.clear();
	LIG_ATOMS.clear(); REC_ATOMS.clear();
	LIG.clear(); REC.clear();
    VB_R.clear();
    VB_L.clear();
    // kdtree deletion
    delete KTREC;
	annDeallocPts(RECdataPts);
    delete KTRATOM;
    annDeallocPts(RECATOMdataPts);
    annClose();
    // If there was a filename provided, close the custom file
    if(!output_filename.empty())
    {
    	(static_cast<ofstream*>(output_stream))->close();
	delete output_stream;
    }
}
