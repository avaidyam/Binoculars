#include <cstdlib>
#include <string>
#include <sstream>
#include <unistd.h>
#include <iostream>

#include "options.h"
#include "constants.h"

namespace opt 
{
    bool usage;
    bool version;

    string msg;

    string rec_file;
    string lig_file;
    string zrinv_file;
    string zlinv_file;
    string pdbrec_file;
    string pdblig_file;
    string intrec_file;
    string intlig_file;
    string output_filename;


    double corr_cutoff;

    double rf_min_dist;
    double rf_max_dist;
    double rfdist_cutoff;
    double fpointdist_cutoff;
    double nradius;
    int nvotes;
    bool applyrandom;


    void DefaultOptions()
    {
        // general options
        usage = false;
        version = false;

        //messages
        msg = "";
    
        rec_file = "";
        lig_file = "";
        zrinv_file = "";
        zlinv_file = "";
        pdbrec_file = "";
        pdblig_file = "";
        intlig_file = "";
        intlig_file = "";

        nvotes = 7;
        applyrandom = false;

        rf_min_dist = 4.;
        rf_max_dist = 9.;
        rfdist_cutoff = 2.;
        fpointdist_cutoff = 12.;
        corr_cutoff = 0.7;
        nradius = 2.0;

	// if it's left empty the output will be printed to
	// standard output
	output_filename = "";
    }

    void ReadCommandLine(int argc, char** argv){
        for (int argnum = 1; argnum<argc; argnum++)
        {
            string arg(argv[argnum]);
            if (arg == "-h" || arg == "-help" || arg == "-?")
            {
                usage = true;
            } 
            else if (arg == "-v" || arg == "-version")
            {
                version = true;
            }
            else if (arg == "-out")
	    {
                string subarg(argv[++argnum]);
                if(subarg.length() != 0)
                {
		    output_filename = subarg;
	    	}
	    }
            else if (arg == "-rec")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -rec requires argument\n";
                    usage = true;
                    break;
                }
                rec_file = subarg;
            }
            else if (arg == "-lig")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -lig requires argument\n";
                    usage = true;
                    break;
                }
                lig_file = subarg;
            }
            else if (arg == "-irec")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -irec requires argument\n";
                    usage = true;
                    break;
                }
                intrec_file = subarg;
            }
            else if (arg == "-ilig")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -ilig requires argument\n";
                    usage = true;
                    break;
                }
                intlig_file = subarg;
            }
            else if (arg == "-prec")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -prec requires argument\n";
                    usage = true;
                    break;
                }
                pdbrec_file = subarg;
            }
            else if (arg == "-plig")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -plig requires argument\n";
                    usage = true;
                    break;
                }
                pdblig_file = subarg;
            }
            else if (arg == "-zrec")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -zrec requires argument\n";
                    usage = true;
                    break;
                }
                zrinv_file = subarg;
            }
            else if (arg == "-zlig")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -zlig requires argument\n";
                    usage = true;
                    break;
                }
                zlinv_file = subarg;
            }
            else if (arg == "-cor")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -cor requires argument\n";
                    usage = true;
                    break;
                }
                corr_cutoff = atof(subarg.c_str());
            }
            else if (arg == "-dist")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -dist requires argument\n";
                    usage = true;
                    break;
                }
                rfdist_cutoff = atof(subarg.c_str());
            }
            else if (arg == "-rfmin")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -rfmin requires argument\n";
                    usage = true;
                    break;
                }
                rf_min_dist = atof(subarg.c_str());
            }
            else if (arg == "-rfmax")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -rfmax requires argument\n";
                    usage = true;
                    break;
                }
                rf_max_dist = atof(subarg.c_str());
            }
            else if (arg == "-rfpmax")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -rfpmax requires argument\n";
                    usage = true;
                    break;
                }
                fpointdist_cutoff = atof(subarg.c_str());
            }
            else if (arg == "-nvotes")
            {
                string subarg(argv[++argnum]);
                nvotes = atoi(subarg.c_str());
            }
            else if (arg == "-nrad")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + ": error: option -nrad requires argument\n";
                    usage = true;
                    break;
                }
                nradius = atof(subarg.c_str());
            }
            else if (arg == "-randomize")
            {
                applyrandom = true;
                break;
            }
            else if (arg[0] == '-')
            {
                msg = APPNAME + ": error: invalid option: " + arg + "\n";
                usage = true;
                break;
            }
        }
    }


    void Check()
    {
        if(rec_file.length() == 0 || lig_file.length() == 0)
        {
            msg = APPNAME + ": error: no critical point files for docking computation.\n";
            usage = true;
            return;
        }
        if(pdbrec_file.length() == 0 || pdblig_file.length() == 0)
        {
            msg = APPNAME + ": error: no pdb files for docking computation.\n";
            usage = true;
            return;
        }
        if(zrinv_file.length() == 0 || zlinv_file.length() == 0)
        {
            msg = APPNAME + ": error: no Zernike invariants for docking computation.\n";
            usage = true;
            return;
        }
        if(corr_cutoff < 0. || corr_cutoff > 1.)
        {
            msg = APPNAME + ": error: Correlation cutoff must be >0 and <1\n";
            usage = true;
            return;
        }
        if(rf_min_dist <= 0. || rf_max_dist <= 0. || fpointdist_cutoff <= 0.)
        {
            msg = APPNAME + ": error: distances must be > 0.\n";
            usage = true;
            return;
        }
        if(rfdist_cutoff < 0.)
        {
            msg = APPNAME + ": error: graph edge length cutoff must be > 0.\n";
            usage = true;
            return;
        }
        if(nradius <= 0)
        {
            msg = APPNAME + ": error: value of neighbouring radius cannot be negative.\n";
            usage = true;
            return;
        }
        if(nvotes < 0)
        {
            msg = APPNAME + ": error: number of votes cannot be negative. Value set to 7.\n";
            nvotes = 6;
            return;
        }
    }

}  // namespace opt
