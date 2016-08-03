/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 * Routines for checking and parsing command line options
 */


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
    bool debug;

    string msg;
    string input_file;// pqr file

    string gts_file; // when previously calculated surface is provided
    bool have_surf; // surface provided thru gts


    // output options
    bool wrl;

    double smooth;
    double cutoff;

    void DefaultOptions()
    {
        // general options
        usage = false;
        version = false;
        debug = false;

        //messages
        msg = "";
    
        input_file = "";
        have_surf = false;

        cutoff = 0.;

        // output format
        wrl = false;
    	smooth = 0.35;
        
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
            else if (arg == "-wrl")
            {
                wrl = true;
            }
            else if (arg == "-cut")
            {
                cutoff = atof(argv[++argnum]);
            }
            else if(arg == "-smooth")
            {
                smooth = atof(argv[++argnum]);
            }
            else if (arg == "-gts")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + " error: option -gts requires argument\n";
                    usage = true;
                    break;
                }
                have_surf = true;
                gts_file = subarg;
            }
            else if (arg == "-pdb")
            {
                string subarg(argv[++argnum]);
                if(subarg.length() == 0)
                {
                    msg = APPNAME + " error: option -pdb requires argument\n";
                    usage = true;
                    break;
                }
                input_file = subarg;
            }
            else if (arg[0] == '-')
            {
                msg = APPNAME + " error: invalid option: " + arg + "\n";
                usage = true;
                break;
            }
        }
    }


    void Check()
    {
        if(input_file.length() == 0)
        {
             msg = APPNAME + " error: no .pdb file supplied.\n";
             usage = true;
             return;
        }
    	if(cutoff < 0.)
        {
            msg = APPNAME + " error: cutoff must be > 0.\n";
			usage = true;
			return;
		}
    }

}  // namespace opt
