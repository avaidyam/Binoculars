#include "contact.h"

double calculate_lrms(vector<atom>& S, vector<atom>& T, map<int, int>& E)
{
    double rmsd = 0.;
    int N = 0;
    map<int, int>::iterator iter;
    for(iter = E.begin();iter!=E.end();++iter)
    {
        int q = iter->first;
        int r = iter->second;
        if(S[q].atype != "CA") continue;
        rmsd += get_distance2(S[q].axyz, T[r].axyz, 3);
        N++;
    }
    if(N != 0)
    {
        rmsd /= (double) N;
        rmsd = sqrt(rmsd);
    }
    else
    {
        cerr << "No matching CA atoms" << endl;
        exit(EXIT_FAILURE);
    }

    return rmsd;
}

double calculate_irmsd(vector<atom>& P, vector<atom>& Q, vector<atom>& X,
    vector<atom>& Y, map<int, int>& MP_R, map<int, int>& MP_L, int k1, int k2)
{
    return align(P, Q, MP_R, k1, X, Y, MP_L, k2);
}

double get_fnat(vector<string>& T, vector<string>& P)
{
    double tf = (double) T.size();
    double nf = 0.;

    vector<string>::iterator pos;
    string str;
    for(size_t i=0;i<T.size();i++)
    {
        pos = find(P.begin(), P.end(), T[i]);
        if(pos == P.end())
            continue;
        nf += 1.0;
    }

    return (nf/tf);
}

