#include "read.h"
#include "utils.h"

// http://periodictable.com/Properties/A/VanDerWaalsRadius.v.html


const double H_RAD = 1.20; // Hydrogen
const double C_RAD = 1.70; // carbon
const double N_RAD = 1.65; // nitrogen
const double O_RAD = 1.60; // oxygen
const double P_RAD = 1.90; // phosphorous
const double S_RAD = 1.80; // sulphur
const double DEF_RAD = 1.70; // default

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
        cerr << "Error: invalid input file name: " << path << endl;
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

void read_point_info(string infile, vector<cp>& cpinfo)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Could not open " << infile << endl;
        exit(EXIT_FAILURE);
    }

    string mLine;

    while(getline(ifs, mLine))
    {
        if(mLine.length() == 0 || (mLine.find("#") != string::npos)) continue;
        istringstream iss(mLine);
        string str[11];
        for(int i=0;i<11;i++)
            iss >> str[i];

        int type = atoi(str[1].c_str());

        double d1[3], d2[3];
        d1[0] = atof(str[2].c_str()); // x coordinate
        d1[1] = atof(str[3].c_str()); // y coordinate
        d1[2] = atof(str[4].c_str()); // z coordinate

        d2[0] = atof(str[5].c_str()); // nx coordinate
        d2[1] = atof(str[6].c_str()); // ny coordinate
        d2[2] = atof(str[7].c_str()); // nz coordinate

        cp ncp(type, d1, d2, str[10], atof(str[8].c_str()), atof(str[9].c_str()));
        cpinfo.push_back(ncp);
    }
    ifs.close();

    if(cpinfo.size() == 0)
    {
        cerr << "Error: no data in file: " << infile << endl;
        exit(EXIT_FAILURE);
    }

    //cerr << "debug: "  << cpinfo.size() << " points in " << infile << endl;
   
}

void read_zd_info(string infile, vector<cp>& cpinfo)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Could not open " << infile << endl;
        exit(EXIT_FAILURE);
    }

    string mLine;
    int lc = 1;

    int N;
    double d[3];

    int k = 0;
    int nzd = 0;
    while(getline(ifs, mLine))
    {
        istringstream iss(mLine);
        if(lc == 1)
        {
            iss >> N;  
        }
        else
        {
            iss >> d[0] >> d[1] >> d[2] >> nzd;
            vector<double> V(nzd);
            for(int i=0;i<nzd;i++)
            {
                iss >> V[i];
            }
            cpinfo[k].ZINV = V;
            k++;
        }
        lc++;
    }
    ifs.close();

}

void read_interface_residues(string infile, vector<pair<string, string> >& RESIDS_CHAINIDS)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Could not open " << infile << endl;
        exit(EXIT_FAILURE);
    }

    string line;
    string s1, s2;

    while(getline(ifs, line))
    {
        istringstream iss(line);
        iss >> s1 >> s2;
        RESIDS_CHAINIDS.push_back(make_pair(s1, s2));
        
    }// end while

    ifs.close();
}

void read_protein(string infile, vector<atom>& P)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Cannot open file: " << infile  << endl;
        exit(EXIT_FAILURE);
    }

    string line;

    int atom_id, res_id;
    string res_name, atom_type;
    double d_xyz[3];
    string chain_id;
	double rad = 0.;

    while(getline(ifs, line))
    {
        if((line.substr(0, 4)).compare("ATOM") == 0)
        {
            atom_id = atoi((line.substr(6, 5)).c_str());
            atom_type = trimmed(line.substr(12, 4), kBlankChars); // hydrogen atoms have 4 letter identification

            if(atom_type == "OT1" || atom_type == "OT2")
            {
                atom_type = "OXT";
            }

            if(atom_type.at(0) == 'H') rad = H_RAD;
            else if(atom_type.at(0) == 'C') rad = C_RAD;
            else if(atom_type.at(0) == 'S') rad = S_RAD;
	        else if(atom_type.at(0) == 'P') rad = P_RAD;
	        else if(atom_type.at(0) == 'O') rad = O_RAD;
	        else if(atom_type.at(0) == 'N') rad = N_RAD;
	        else rad = DEF_RAD;

            res_name = trimmed(line.substr(17, 3), kBlankChars);
            chain_id = trimmed(line.substr(21, 1), kBlankChars);
            res_id = atoi((line.substr(22, 4)).c_str());

            //cout << chain_id << " " << res_id << endl;
            d_xyz[0] = atof((line.substr(30, 8)).c_str());
            d_xyz[1] = atof((line.substr(38, 8)).c_str());
            d_xyz[2] = atof((line.substr(46, 8)).c_str());

            // create atom object
            atom n_atom(atom_id, atom_type, res_name, chain_id, res_id, d_xyz, rad);
            P.push_back(n_atom);
        }
    }// end while

    ifs.close();
}

void read_names(string infile, vector<string>& V)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Cannot open file: " << infile  << endl;
        exit(EXIT_FAILURE);
    }

    string line;

    while(getline(ifs, line))
    {
        if(line.length() > 0 && trimmed(line, kBlankChars) != "")
            V.push_back(line);
    }

    ifs.close();
}

