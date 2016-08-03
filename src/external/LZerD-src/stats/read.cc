#include "read.h"
#include "utils.h"


/**
 * Read PQR informtion from file
 * Parse only "ATOM" information.
 *
 * @see    process_files
 * @params infile name of pqr file
 * @params pdbdata stores pqr coordinates and atom types
 * @return void
 */

void read_protein(string infile, pdb& pdbdata)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Could not open file: " << infile << endl;
        exit(EXIT_FAILURE);
    }

    string line;

    int atom_id, res_id;
    string chain_id;
    string res_name, atom_type, achar;
    double d_xyz[3];

    vector<atom> vct_atom;

    while(getline(ifs, line))
    {
        if((line.substr(0, 4)).compare("ATOM") == 0)
        {
            atom_id = atoi((line.substr(6, 5)).c_str());
            atom_type = trimmed(line.substr(12, 4), kBlankChars); // hydrogen atoms have 4 letter identification
            res_name = trimmed(line.substr(17, 3), kBlankChars);
            chain_id = trimmed(line.substr(21, 1), kBlankChars);
            res_id = atoi((line.substr(22, 4)).c_str());
            //cout << res_id << endl;
            d_xyz[0] = atof((line.substr(30, 8)).c_str());
            d_xyz[1] = atof((line.substr(38, 8)).c_str());
            d_xyz[2] = atof((line.substr(46, 8)).c_str());

            achar = line.length() > 76 ? trimmed(line.substr(76, 2), kBlankChars) : "";

            // create atom object
            atom n_atom(atom_id, atom_type, res_id, d_xyz, res_name, achar, chain_id);

            vct_atom.push_back(n_atom);
        }
    }// end while


    string fname = get_file_name(infile);
    
    pdbdata.atoms = vct_atom;
    pdbdata.pname = fname;

    ifs.close();
}



void read_predictions(string infile, vector<vector<double> >& Z, double* transL, double* rotL)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Could not open file: " << infile << endl;
        exit(EXIT_FAILURE);
    }

    string line;

    double t[13];
    while(getline(ifs, line))
    {
        if(line.find("LIG:") != string::npos)
        {
            istringstream iss(line);
            string s;
            iss >> s >> rotL[0] >> rotL[1] >> rotL[2] >> transL[0] >> transL[1] >> transL[2];
            continue;
        }

        vector<double> V;
        istringstream iss(line);
        for(int i=0;i<13;i++)
           iss >> t[i];
        for(int i=0;i<13;i++)
            V.push_back(t[i]);
        //cerr << V[12] << endl;           
        Z.push_back(V);
    }
    ifs.close();

    if(Z.size() == 0)
    {
        cerr << "Error: no data to process" << endl;
        exit(EXIT_FAILURE);
    }
}


void read_zdock_data(string infile, string& fixed, string& mov, zdata& Z)
{
    ifstream ifs;
    ifs.open(infile.c_str());
    if(!ifs)
    {
        cerr << "Could not open file: " << infile << endl;
        exit(EXIT_FAILURE);
    }

    string s1, s2;
    
    string line;
    int lnc = 0;
    double t1[3];
    int t2[3];
    while(getline(ifs, line))
    {
        lnc = lnc + 1;
        istringstream iss(line);
        if(lnc == 1)
        {
            iss >> Z.N >> Z.SPACING;
        }
        if(lnc == 2)
        {
            iss >> Z.RANDVECT[0] >> Z.RANDVECT[1] >> Z.RANDVECT[2];
        }
        if(lnc == 3)
        {
            iss >> s1 >> Z.RVECT[0] >> Z.RVECT[1] >> Z.RVECT[2];
            //fixed = get_file_name(s1) + ".pqr";
            fixed = s1;
        }
        if(lnc == 4)
        {
            iss >> s2 >> Z.LVECT[0] >> Z.LVECT[1] >> Z.LVECT[2];
            //mov = get_file_name(s2) + ".pqr";
            mov = s2;
        }
        if(lnc > 4)
        {
            
            iss >> t1[0] >> t1[1] >> t1[2] >> t2[0] >> t2[1] >> t2[2];
            zdock nz(t1, t2);
            Z.ZD.push_back(nz);
        }
    }
    ifs.close();

    if(Z.ZD.size() == 0)
    {
        cerr << "Error: no data to process" << endl;
        exit(EXIT_FAILURE);
    }
}
