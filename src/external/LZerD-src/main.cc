#include "options.h"
#include "read.h"
#include "gh.h"
#include "utils.h"

#include <ctime>
#include <sstream>
#include <iomanip>
#include "constants.h"

#ifdef WITH_MPI

// only include if the mpi version is being compiled
#include "mpi.h"

#endif



void print_usage()
{
    cout << "\nUsage:" << APPNAME << " [options] \n" << endl;
    cout << "Options:" << endl;
    cout << "  -h[elp]        display this helpful message" << endl;
    cout << "  -v[ersion]     display the program's version number" << endl;
    cout << "  -out           filename where output will be written" << endl;
    cout << "  -rec ifile     input critical point file for receptor" << endl;
    cout << "  -lig ifile     input critical point file for ligand" << endl;
    cout << "  -prec ifile    input pdb file for receptor" << endl;
    cout << "  -plig ifile    input pdb file for ligand" << endl;
    cout << "  -irec ifile    interface residues file for receptor" << endl;
    cout << "  -ilig ifile    interface residues file for ligand" << endl;
    cout << "  -rfmin         min distance for reference frame points. [DEFAULT 4.0]" << endl;
    cout << "  -rfmax         max distance for reference frame points. [DEFAULT 9.0]" << endl;
    cout << "  -zrec ifile    Zernike invariants file for receptor" << endl;
    cout << "  -zlig ifile    Zernike invariants file for ligand" << endl;
    cout << "  -cor  value    Zernike Correlation cutoff. [DEFAULT 0.70]" << endl;
    cout << "  -dist  value   distance cutoff for comparable distances. [DEFAULT 2.0]" << endl;
    cout << "  -rfpmax value  distance cutoff for the fourth point. [DEFAULT 15.0]" << endl;
    cout << "  -nvotes value  vote threshold. [DEFAULT 7]" << endl;
    cout << "  -nrad value    neighbourhood radius for nodes to be considered during voting. [DEFAULT 2.]" << endl;
    cout << "  -randomize     apply a random rotation to the ligand. Default is no rotation" << endl;
    cout << "########################################################################\n";
    cout << " Interface residues to be listed in the form RESIDUE+RESIDUE_ID CHAIN_ID\n";
    cout << " ALA17 C\n";
    cout << " CYS32 C\n";
    cout << " PHY67 A\n";
    cout << "########################################################################\n";
    cout << "\nReport bugs to <vvenkatr@purdue.edu>\n" << endl;
}



// keep receptor fixed
// apply translation to origin and rotation to ligand

void process_files(string rec, string lig, string zrec, string zlig,
    string prec, string plig, string irec, string ilig, double corr,
    double rfmin, double rfmax, double rfdist, double dcut, int votes,
    double nrad, bool applyrandmotion,
    string output_filename)
{
	// obtain a random rotation and cog translation for the original unbound ligand
	// apply the same to all conformations

    // read the ligand information
    vector<cp> LIG_CPS;
    vector<atom> LIG_ATOMS;
    // read critical point info
    read_point_info(lig, LIG_CPS);
    // read zernike
    read_zd_info(zlig, LIG_CPS);
    // read atoms
    read_protein(plig, LIG_ATOMS);

    vector<cp> REC_CPS;
    vector<atom> REC_ATOMS;
    // read critical point info
    read_point_info(rec, REC_CPS);
    // read zernike
    read_zd_info(zrec, REC_CPS);
    // read atoms
    read_protein(prec, REC_ATOMS);
    
    cerr << "Checking Receptor critical points...\n";
    bool rec_success = check_cp(REC_CPS);
    cerr << "Checking Ligand critical points...\n";
    bool lig_success = check_cp(LIG_CPS);

    if((!rec_success) || (!lig_success)) {
    	// Abort because these critical points should be ignored due to inconsistencies
	cerr << "Error: Faulty critical points found. Remove from *.inv and *.txt files (CP Warnings are zero-indexed)\n";
    	return;
    }

    // implement cp filter if predicted interface residues are given
    // collect all residues that fall in the sphere (CA, CB atoms)
    if(irec.length() != 0)
    {
        vector<pair<string, string> > RESIDS_CHAINIDS;
        read_interface_residues(irec, RESIDS_CHAINIDS);
        filter_cp(REC_CPS, REC_ATOMS, RESIDS_CHAINIDS);
        if(REC_CPS.size() == 0)
        {
            cerr << "No CPs in receptor file. Exiting..." <<  endl;
            exit(EXIT_SUCCESS);
        }
    }
    if(ilig.length() != 0)
    {
        vector<pair<string, string> > RESIDS_CHAINIDS;
        read_interface_residues(ilig, RESIDS_CHAINIDS);
        filter_cp(LIG_CPS, LIG_ATOMS, RESIDS_CHAINIDS);
        if(LIG_CPS.size() == 0)
        {
            cerr << "No CPs in receptor file. Exiting..." <<  endl;
            exit(EXIT_SUCCESS);
        }
    }
    
    invert_normals(LIG_CPS);
    // apply random rotation to ligand
    if(applyrandmotion)
        apply_random_rotation(LIG_ATOMS, LIG_CPS);

    cerr << "REC: " << REC_CPS.size() << " LIG: " << LIG_CPS.size() << endl;

    dock(REC_CPS, REC_ATOMS, LIG_CPS, LIG_ATOMS, corr, rfmin, rfmax, rfdist, dcut, votes, nrad, output_filename);
}

int main(int argc, char** argv)
{
#ifdef WITH_MPI
    /*** Perform the MPI Initialization that applies to all ranks ***/
    MPI_Init(&argc,&argv);
#endif

    time_t start_time, end_time;
    start_time = time(NULL);    //record time that task begins

    //parse options
    // process command line
    opt::DefaultOptions();
    opt::ReadCommandLine(argc, argv);

    if(!opt::usage)
    {
        opt::Check();
    }

    if(opt::version)
    {
        cout << VERSION << endl;
        cout << COPYRIGHT << endl;
        exit(EXIT_SUCCESS);
    }

    if(opt::usage)
    {
        cout << opt::msg << endl;
        print_usage();
        exit(EXIT_SUCCESS);
    }

    process_files(opt::rec_file, opt::lig_file, opt::zrinv_file,
        opt::zlinv_file, opt::pdbrec_file, opt::pdblig_file,
        opt::intrec_file, opt::intlig_file, opt::corr_cutoff, opt::rf_min_dist,
        opt::rf_max_dist, opt::fpointdist_cutoff, opt::rfdist_cutoff, opt::nvotes,
        opt::nradius, opt::applyrandom, opt::output_filename);

    end_time = time(NULL);    //record time that task 1 ends
    cerr << APPNAME << " done in a total of " << setw(4)
         << difftime(end_time, start_time) << " seconds\n";

#ifdef WITH_MPI
    MPI_Finalize();
#endif

    exit(EXIT_SUCCESS);
}
