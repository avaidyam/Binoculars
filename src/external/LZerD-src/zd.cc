#include "gh.h"
#include <cfloat>


double zd_correlation(vector<double>& A, vector<double>& B)
{
    double sum_A = 0., sum_B = 0., sum_AB = 0.;
    double sum_sq_A = 0., sum_sq_B = 0.;
    for(size_t i=0;i<A.size();i++)
    {
        sum_A += A[i];
        sum_B += B[i];
        sum_AB += A[i]*B[i];
        sum_sq_A += A[i]*A[i];
        sum_sq_B += B[i]*B[i];
    }

    double num = (A.size())* sum_AB - sum_A*sum_B;

    double d1 = (A.size()) * sum_sq_A - sum_A*sum_A;
    double d2 = (A.size()) * sum_sq_B - sum_B*sum_B;

    double den = sqrt(d1) * sqrt(d2);

    if(isnan(num/den))
    {
        return 0.;
    }

    return (num/den);
}


double zd_distance(vector<double>& A, vector<double>& B)
{
    double d = 0.;
    for(size_t i=0;i<A.size();i++)
    {
        d += (A[i]-B[i])*(A[i]-B[i]);
    }
    return sqrt(d);
}

void calculate_euclidean_distances(vector<cp>& LIG, vector<cp>& REC, vector<vector<double> >& MED)
{
    int iL = (int) LIG.size();
    int iR = (int) REC.size();


    for(int i=0;i<iL;i++)
    {
        for(int j=0;j<iR;j++)
        {
            MED[i][j] = zd_distance(LIG[i].ZINV, REC[j].ZINV);
        }
    }
}


void calculate_correlation_values(vector<cp>& LIG, vector<cp>& REC, vector<vector<double> >& MCOR)
{
    int iL = (int) LIG.size();
    int iR = (int) REC.size();

    for(int i=0;i<iL;i++)
    {
        for(int j=0;j<iR;j++)
        {
            MCOR[i][j] = zd_correlation(LIG[i].ZINV, REC[j].ZINV);
        }
    }
}
