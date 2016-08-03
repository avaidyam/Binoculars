/**
 * * @date   Sep/27/2007
 */

#include <vector>
#include <string>
#include <cmath>
#include <cfloat>
#include <string>
#include <valarray>
#include <map>

using namespace std;

#include "gts.h"

#ifndef _ATOM_H_
#define _ATOM_H_

class atom
{
    public:
        int         anum;   // id of the atom
        string      atype;  // type of the atom
        int         rnum;   // residue id
        double      axyz[3]; // xyz coordinates
        string      residue; // name of residue
        string      chain;// chain id
        int         satom; // surface atom 0/1
        double      atom_rad;
        double      atom_charge;

        atom(int m_num, string m_type, string m_residue, string m_chain, int m_rid, double* m_xyz, double m_rad, int m_sa,
            double m_charge)
        {
            anum = m_num;
            rnum = m_rid;
            atype = m_type;
            for(int i=0;i<3;i++)
            {
                axyz[i] = m_xyz[i];
            }
            residue = m_residue;
            chain = m_chain;
            satom = m_sa;
            atom_rad = m_rad;
            atom_charge = m_charge;
        }

        atom(int m_num, string m_type, string m_residue, int m_rid, double* m_xyz, double m_charge, double m_rad)
        {
            anum = m_num;
            rnum = m_rid;
            atype = m_type;
            for(int i=0;i<3;i++)
            {
                axyz[i] = m_xyz[i];
            }
            residue = m_residue;
            atom_rad = m_rad;
            atom_charge = m_charge;
            satom = 0;
        }

        atom(){}
        ~atom(){}
};

#endif

#ifndef _VINDEX_H_
#define _VINDEX_H_

class vindex
{
    public:
        int ID;
        double DIST;

        vindex(int i, double d)
        {
            ID = i;
            DIST = d;
        }
        ~vindex(){}
};

#endif

#ifndef _EDGE_H_
#define _EDGE_H_

class edge
{
    public:
        int START;
        int END;
        edge(int i, int j)
        {
            START = i;
            END = j;
        }
        ~edge(){}
};

#endif

#ifndef _CRITICAL_POINT_H_
#define _CRITICAL_POINT_H_
class criticalpoint
{
public:
    int cpid;
    int ctype;
    double coords[3];
    double normal[3];
    double CURV;
    double MEP;
    string nres;
    criticalpoint(int t0, int t1, double* f1, double* f2, double f3, double f4)
    {
        cpid = t0;
        ctype = t1;
        for(int i=0;i<3;i++)
        {
            coords[i] = f1[i];
            normal[i] = f2[i];
        }
        CURV = f3;
        nres = "";
        MEP = f4;
    }
    criticalpoint(){}
    ~criticalpoint(){}
};

#endif


#ifndef _ISURFACE_H_
#define _ISURFACE_H_

class isurface
{
    public:
        string pdbname;
        vector<atom> atoms;
        GtsSurface* isurf;
        vector<double> cur_value;
        map<int, vector<int> > NN_MAP; // vertex neighbours
        map<int, int> VTYPE_MAP; // vertex id, type
        vector<criticalpoint> RVCP;
        int NSA;

        isurface(){}
        ~isurface()
        {
            if(isurf)
                gts_object_destroy(GTS_OBJECT (isurf));
        }
};

#endif


#ifndef _CPOINT_H_
#define _CPOINT_H_

class cpoint
{
public:
    static const cpoint ERROR;
    int idx;
    double x, y, z;
    cpoint(){}
    ~cpoint(){}
    bool operator == (const cpoint& v)
    {
        return ((v.idx == idx) && (v.x == x) && (v.y == y) && (v.z == z));
    }   
};

#endif


#ifndef _OPOINT_H_
#define _OPOINT_H_

class opoint
{
public:
    int idx;
    double ALPHA, BETA;

    opoint(){}
    opoint(double a, double b)
    {
        ALPHA = a;
        BETA = b;
    }
    ~opoint(){}
};

#endif
