/**
 *  Utilities for reading MSMS output
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 */
 
 
#include "read.h"
#include "classes.h"

#include <cmath>
#include <sstream>

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
        cerr << "invalid input file name: " << path << endl;
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

    // strip ".vert"/".face" from the filename
    string fname = "";
    if(path.find(".ms") != string::npos)
        fname = in_file.erase(in_file.length()-7);
    else
        fname = in_file.erase(in_file.length()-4);


    return fname;
}


/**************************** PDB READING ROUTINES ********************************/

void read_pdb(string infile, isurface& msurf)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Cannot open file: " << infile << endl;
        exit(EXIT_FAILURE);
    }

    string line;

    int atom_id, res_id;
    string res_name, atom_type;
    double d_xyz[3], arad = 0., chg = 0.;
    string chain_id;

    int sa = 1;

    vector<atom> vct_atom;
    int nsa = 0;

    while(getline(ifs, line))
    {
        if((line.substr(0, 4)).compare("ATOM") == 0)
        {
            atom_id = atoi((line.substr(6, 5)).c_str());
            atom_type = trimmed(line.substr(12, 4), kBlankChars); // hydrogen atoms have 4 letter identification
            res_name = trimmed(line.substr(17, 3), kBlankChars);
            chain_id = trimmed(line.substr(21, 1), kBlankChars);
            res_id = atoi((line.substr(22, 4)).c_str());

            // hack for HSE residue type
            if(res_name.compare("HSE") == 0) res_name = "HIS";
            // hack for MSE residue type
            if(res_name.compare("MSE") == 0) res_name = "MET";

            sa = atoi((line.substr(62, 1)).c_str());
            if(sa == 1)
                nsa++;

            d_xyz[0] = atof((line.substr(30, 8)).c_str());
            d_xyz[1] = atof((line.substr(38, 8)).c_str());
            d_xyz[2] = atof((line.substr(46, 8)).c_str());
            arad = atof((line.substr(64, 5)).c_str());

            chg = atof((line.substr(77, 5)).c_str());

            // create atom object
            atom n_atom(atom_id, atom_type, res_name, chain_id, res_id, d_xyz, arad, sa, chg);

            vct_atom.push_back(n_atom);
        }
        line.clear();
    }// end while

    ifs.close();


    msurf.pdbname = get_file_name(infile);
    msurf.atoms = vct_atom;
    msurf.NSA = nsa;
}

void read_pqr(string infile, isurface& msurf)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Cannot open file: " << infile << endl;
        exit(EXIT_FAILURE);
    }

    string line;

    int atom_id, res_id;
    string res_name, atom_type;
    double d_xyz[3], atom_charge, a_rad;

    vector<atom> vct_atom;

    while(getline(ifs, line))
    {
        if((line.substr(0, 4)).compare("ATOM") == 0)
        {
            atom_id = atoi((line.substr(6, 5)).c_str());
            atom_type = trimmed(line.substr(12, 4), kBlankChars); // hydrogen atoms have 4 letter identification
            res_name = trimmed(line.substr(17, 3), kBlankChars);

            // hack for HSE residue type
            if(res_name.compare("HSE") == 0) res_name = "HIS";
            // hack for MSE residue type
            if(res_name.compare("MSE") == 0) res_name = "MET";

            res_id = atoi((line.substr(22, 4)).c_str());
            d_xyz[0] = atof((line.substr(30, 8)).c_str());
            d_xyz[1] = atof((line.substr(38, 8)).c_str());
            d_xyz[2] = atof((line.substr(46, 8)).c_str());
            atom_charge = atof((line.substr(55, 8)).c_str());
            a_rad = atof((line.substr(63, 6)).c_str());

            // create atom object
            atom n_atom(atom_id, atom_type, res_name, res_id, d_xyz, atom_charge, a_rad);
            vct_atom.push_back(n_atom);
        }
        line.clear();
    }// end while

    ifs.close();

    // assign surface area
    int nsa = 0;
    calculate_sas(vct_atom, nsa);


    msurf.pdbname = get_file_name(infile);
    msurf.atoms = vct_atom;
    msurf.NSA = nsa;
}
