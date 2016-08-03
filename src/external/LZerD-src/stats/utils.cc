#include "utils.h"


/// Returns a string with leading/trailing characters of a set stripped
string trimmed(string const& str, char const* sepSet=kBlankChars)
{
    string::size_type const first = str.find_first_not_of(sepSet);
    return (first==string::npos )? string():str.substr(first, str.find_last_not_of(sepSet)-first+1);
}

string rtrimmed(string const& str, char const* sepSet)
{
    string::size_type const last = str.find_last_not_of(sepSet);
    return (last==string::npos)? string():str.substr(0, last+1);
}

string ltrimmed(string const& str, char const* sepSet)
{
    string::size_type const first = str.find_first_not_of(sepSet);
    return (first==string::npos)? string():str.substr(first);
}

/**
 * point_transform:
 * @p: a Point.
 * @m: the Matrix representing the transformation to 
 * @origin: orgin of the reference frame
 * apply to the coordinates of @p.
 *
 * Transform the coordinates of @p according to @m. (p[] becomes m[][].p[]).
 */


void point_transform (double* p, T43Matrix m)
{
    double x, y, z;
    x = m[0][0]*p[0] + m[0][1]*p[1] + m[0][2]*p[2] + m[3][0];
    y = m[1][0]*p[0] + m[1][1]*p[1] + m[1][2]*p[2] + m[3][1];
    z = m[2][0]*p[0] + m[2][1]*p[1] + m[2][2]*p[2] + m[3][2];
    p[0] = x; p[1] = y; p[2] = z;
}

// tranform coordinates for the ligand
void transform(T43Matrix R, vector<atom>& ain, vector<atom>& atrans)
{
    for(size_t i=0;i<ain.size();i++)
    {
        atom t = ain[i];
        point_transform(t.axyz, R);
        atrans.push_back(t);
    }
}


void rotate_atom(double oldX, double oldY, double oldZ,
                 double& newX, double& newY, double& newZ,
                 double phi, double theta, double psi)
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


void apply_rotation(pdb& P, double* rot)
{
    double tx1, ty1, tz1, oldx, oldy, oldz;
    double rand1 = rot[0], rand2 = rot[1], rand3 = rot[2];
    vector<atom> NB;
    for(size_t i=0;i<P.atoms.size();i++)
    {
        atom RA = P.atoms[i];
        oldx = RA.axyz[0];
        oldy = RA.axyz[1];
        oldz = RA.axyz[2];

        rotate_atom(oldx, oldy, oldz, tx1, ty1, tz1, rand1, rand2, rand3);
        RA.axyz[0] = tx1;RA.axyz[1] = ty1;RA.axyz[2] = tz1;

        NB.push_back(RA);
    }
    P.atoms.clear();
    P.atoms = NB;
}

void translate_atoms(pdb& C, double* cog)
{
    for(size_t i=0;i<C.atoms.size();i++)
    {
        for(int j=0;j<3;j++)
        {
             C.atoms[i].axyz[j] += cog[j];
        }
    }
}

string to_string(int t)
{
    stringstream ss;
    ss << t;
    return ss.str();
}

void print_complex(int complex_id, vector<atom>& R, vector<atom>& L)
{
    string pdb_file_name = "complex" + to_string(complex_id) + ".pdb";
    FILE        *pdb_file ;
    if((pdb_file = fopen( pdb_file_name.c_str(), "w")) == NULL)
    {
        printf( "This file could not be opened.\nDying\n\n" ) ;
        exit(EXIT_FAILURE);
    }

    vector<atom> P;
    //P = R;
    for(size_t i=0;i<L.size();i++)
    {
        P.push_back(L[i]);
    }

    for(size_t i=0;i<P.size();i++)
    {
        fprintf(pdb_file, "ATOM  %5d %4s %3s %1s%5d   %8.3f%8.3f%8.3f\n",
            P[i].anum, P[i].atype.c_str(), P[i].residue.c_str(), P[i].chain.c_str(), P[i].rnum, P[i].axyz[0], P[i].axyz[1], P[i].axyz[2]);
    }
    fclose(pdb_file);
}


/**
 * get_distance:
 * @p: point 1
 * @q: point 2
 * Calculate euclidean get_distance between points.
 */

double get_distance(double *p , double *q, int k)
{
    double f = 0.;
    for(int i=0;i<k;i++)
        f += (p[i] - q[i])*(p[i] - q[i]);
    return sqrt(f);
}

double get_distance2(double *p , double *q, int k)
{
    double f = 0.;
    for(int i=0;i<k;i++)
        f += (p[i] - q[i])*(p[i] - q[i]);
    return f;
} 

/**
 * return the filename after removing the path
 *
 * @params path filepath
 * @return basename of the file
 */
string basename(const string& path)
{
    string::size_type idx = path.find_last_of("\\/");

    if (idx == path.length()-1)
    {
        // last character is a path separator; i.e. path is a directory
        cerr << "invalid input file name" << endl;
        exit(EXIT_FAILURE);
    }

    if (idx != string::npos)
    {
        return path.substr(idx+1);
    }
    else
    {
        return path;
    }
}

/**
 * return the filename after removing the path and extension
 *
 * @params path filepath
 * @return actual file name
 */
string get_file_name(const string& path)
{
    // remove directory path from the filename
    string in_file = basename(path);

    // TODO: check the filename ends pdb

    // strip ".pdb"/".pqr" from the filename
    string fname = in_file.erase(in_file.length()-4);

    return fname;
}



