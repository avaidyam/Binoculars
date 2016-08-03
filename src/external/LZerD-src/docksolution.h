#ifndef _DOCK_SOLUTION_H_
#define _DOCK_SOLUTION_H_

class docksolution
{
public:
    double SCORE;
    int REC_ID;
    int LIG_ID;
    docksolution(double d, int l, int m)
    {
        SCORE = d;
        REC_ID = l;
        LIG_ID = m;
    }
};

#endif
