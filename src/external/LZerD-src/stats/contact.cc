#include "contact.h"

int get_CA(string res, int rnum, string chain, vector<atom>& B)
{
    for(size_t i=0;i<B.size();i++)
    {
        if(B[i].atype == "CA")
        {
            if(res == B[i].residue && rnum == B[i].rnum && chain == B[i].chain)
                return (int) i;
        }
    }
    return -1;
}


void set_CA(map<int, int>& M, vector<atom>& A, vector<atom>& B, int& n)
{
    n = 0;
    int k = -1;
    for(size_t i=0;i<A.size();i++)
    {
        if(A[i].atype.compare("CA") == 0 && A[i].INTFA == 1)
        {
            k = get_CA(A[i].residue, A[i].rnum, A[i].chain, B);
            M.insert(make_pair((int) i, k));
            if(k != -1) n++;
        }
    }
}





void set_interface_calpha(vector<atom>& X, string chain, string res, int rnum)
{
    for(size_t i=0;i<X.size();i++)
    {
        if(X[i].atype == "CA")
        {
            if(res == X[i].residue && rnum == X[i].rnum && chain == X[i].chain)
            {
                X[i].INTFA = 1;
                break;
            }
        }
    }
}

void get_calpha_pos(vector<atom>& X, ANNkd_tree* T)
{
    ANNpoint f = annAllocPt(3);
    double r2 = 100.0;
    for(size_t i=0;i<X.size();i++)
    {
        f[0] = X[i].axyz[0];
        f[1] = X[i].axyz[1];
        f[2] = X[i].axyz[2];
        ANNidxArray nnIdx = NULL;
        ANNdistArray dists = NULL;

        int nnode = T->annkFRSearch(f, r2, 0, nnIdx, dists, 0.); 
        // set the CA atom of the residue to which this atom belongs            
        if(nnode > 0)
            set_interface_calpha(X, X[i].chain, X[i].residue, X[i].rnum);
    }
}

void check_CA(map<int, int>& M, int RL)
{
    int n1 = 0, n2 = 0;
    map<int, int>::iterator it;
    int f1, f2;
    for(it = M.begin();it!=M.end();++it)
    {
        f1 = it->first;
        f2 = it->second;
        if(f1 != -1)
            n1++;
        if(f2 != -1)
            n2++;
    }
    if(n2 == 0)
    {
        if(RL == 0)
        {
            cerr << "No matching CA in the 2 receptor structures" << endl;
        }
        else
        {
            cerr << "No matching CA in the 2 ligand structures" << endl;
        }
        cerr << "Original: " << n1 << endl;
        cerr << "Predicted: " << n2 << endl;
        exit(EXIT_FAILURE);
    }
    else if(n1 != n2)
    {
        cerr << "Some residues were not matched" << endl;
        cerr << "Original: " << n1 << endl;
        cerr << "Predicted: " << n2 << endl;
    }
}


void get_interface_residues(vector<atom>& R, vector<atom>& L)
{
    // get KDTree for R and L
    // get KDTree for receptor
    int N = (int) R.size();
    ANNpointArray RdataPts;
    RdataPts = annAllocPts(N, 3);

    for(int i=0; i<N; i++)
    {
        for(int j=0;j<3;j++)
            RdataPts[i][j] = R[i].axyz[j];
    }
    ANNkd_tree *TR = new ANNkd_tree(RdataPts, N, 3);

    N = (int) L.size();
    ANNpointArray LdataPts;
    LdataPts = annAllocPts(N, 3);

    for(int i=0; i<N; i++)
    {
        for(int j=0;j<3;j++)
            LdataPts[i][j] = L[i].axyz[j];
    }
    ANNkd_tree *TL = new ANNkd_tree(LdataPts, N, 3);

    get_calpha_pos(R, TL);
    get_calpha_pos(L, TR);

    // kdtree deletion
    delete TR;
    annDeallocPts(RdataPts);
    annClose();    
    // kdtree deletion
    delete TL;
    annDeallocPts(LdataPts);
    annClose();
}



// LRMSD

void get_residue_list(vector<atom>& L,  vector<string>& IRES)
{
    set<string> VRES;
    for(size_t i=0;i<L.size();i++)
    {
        string str =  L[i].residue + to_string(L[i].rnum);
        VRES.insert(str);
    }
    set<string>::iterator it;
    for(it=VRES.begin();it!=VRES.end();it++)
    {
        IRES.push_back(*it);
    }
}

int get_CA(string res, vector<atom>& B)
{
    string str = "";
    for(size_t i=0;i<B.size();i++)
    {
        if(B[i].atype.compare("CA") == 0)
        {
            str = B[i].residue + to_string(B[i].rnum);
            if(str.compare(res) == 0)
                return (int) i;
        }
    }
    return -1;
}

void set_ligand_CA(map<int, int>& M, vector<string>& S, vector<atom>& A, vector<atom>& B, int& n)
{
    n = 0;
    string str;
    int k = -1;
    vector<string>::iterator it;
    for(size_t i=0;i<A.size();i++)
    {
        if(A[i].atype.compare("CA") == 0)
        {
            str = A[i].residue + to_string(A[i].rnum);
            it = find(S.begin(), S.end(), str);
            if(it != S.end())
            {
                k = get_CA(str, B);
                if(k != -1)
                {
                    n++;
                    M.insert(make_pair((int) i, k));
                }
            }
        }
    }
}

void check_ligand_CA(map<int, int>& M)
{
    int n1 = 0, n2 = 0;
    map<int, int>::iterator it;
    int f1, f2;
    for(it = M.begin();it!=M.end();++it)
    {
        f1 = it->first;
        f2 = it->second;
        if(f1 != -1)
            n1++;
        if(f2 != -1)
            n2++;
    }
    if(n2 == 0)
    {
        cerr << "No matching CA in the 2 ligand structures" << endl;
        cerr << "Original: " << n1 << endl;
        cerr << "Predicted: " << n2 << endl;
        exit(EXIT_FAILURE);
    }
}


// FNAT CALCULATION

// 2 residues are considered as being in contact if at least one atom of one residue is
// within 5Å of an atoms of the other.
void identify_contact_residues(vector<atom>& R, vector<atom>& L, ANNkd_tree* T, vector<string>& MP)
{
    int dim = 3;
    ANNpoint f = annAllocPt(dim);

    double r2 = 25.0;
    set<string> Z;
    
    for(size_t i=0;i<L.size();i++)
    {
        f[0] = L[i].axyz[0];
        f[1] = L[i].axyz[1];
        f[2] = L[i].axyz[2];

        ANNidxArray nnIdx = NULL;
        ANNdistArray dists = NULL;

        int nnode = T->annkFRSearch(f, r2, 0, nnIdx, dists, 0.); // search for n of them.
        if(nnode <= 0)
            continue;

        nnIdx = new ANNidx[nnode];
        dists = new ANNdist[nnode];
        T->annkFRSearch(f, r2, nnode, nnIdx, dists, 0.); // search for n of them.

        for(int p1=0;p1<nnode;p1++)
        {
            int k = nnIdx[p1];
            string ar = R[k].chain + to_string(R[k].rnum) + R[k].residue;
            string br = L[i].chain + to_string(L[i].rnum) + L[i].residue;
            Z.insert(ar + "_" + br);
        }
        delete [] nnIdx;
        delete [] dists;

    }
    set<string>::iterator iter;
    for(iter=Z.begin();iter!=Z.end();++iter)
    {
        MP.push_back(*iter);
    }
}
