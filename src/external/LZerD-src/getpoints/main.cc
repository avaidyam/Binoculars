/**
 * @author Vishwesh Venkatraman
 * @date   Sep/27/2007
 *
 * Program for calculating critical points of a Morse function
 * For algorithm details please refer
 * Salient Critical Points for Meshes
 * Liu, Y., Liu, M., Kihara, D. and Ramani, K.
 * 
 * Program requires a triangulated surface as input, along with property 
 * (MEP/Curvature) and a saliency criterion. 
 * Program outputs a VRML file showing the critical points (maxima, minima 
 * and saddles) and  a separate text file with values and coordinates. 
 */

#include "read.h"
#include "cp.h"
#include "options.h"
#include "constants.h"
#include "surface.h"
#include "output.h"

#include <ctime>
#include <iomanip>
#include <sstream>

void print_usage()
{
    cout << "\nUsage:" << APPNAME << " [options] pdb_file\n" << endl;
    cout << "where the input file is a .pdb.ms file" << endl;
    //cout << "Please see " << APPNAME << " manual for details" << endl;
    cout << "Options:" << endl;
    cout << "  -h[elp]         display this helpful message" << endl;
    cout << "  -v[ersion]      display the program's version number" << endl;
    cout << "  -pdb.ms ifile   input .pdb.ms file" << endl;
    cout << "  -gts ifile      input gts file with surface data" << endl;
    cout << "  -cut value      cutoff for surface reduction" << endl;
    cout << "  -wrl            output in VRML 2.0 format" << endl;
    cout << "  -smooth         surface smoothness" << endl;
    cout << "\nReport bugs to <vvenkatr@purdue.edu>\n" << endl;
}

void write_output(isurface& msurf, ofstream& ofs)
{
    write_text(msurf, ofs);
    if(opt::wrl)
        write_wrl(msurf);
}



void process_file(string protein_file)
{
    isurface msurf;
    if(protein_file.find(".pdb") != string::npos)
        read_pdb(protein_file, msurf);
    else
        read_pqr(protein_file, msurf);

    if(opt::have_surf)
    {
        msurf.isurf = gts_surface_new (gts_surface_class (),
                        gts_face_class (),
                        gts_edge_class (),
                        gts_vertex_class ());

        FILE *fc = fopen(opt::gts_file.c_str(), "r");
        GtsFile * fp = gts_file_new(fc);
        if(gts_surface_read (msurf.isurf, fp))
        {
            cerr << "the file on standard input is not a valid GTS file\n" << endl;
            fprintf (stderr, "stdin:%d:%d: %s\n", fp->line, fp->pos, fp->error);
            exit(EXIT_FAILURE); 
        }
        fclose(fc);
        gts_file_destroy(fp);

        if(opt::cutoff > 0.)
            surface_clean(msurf.isurf, opt::cutoff); // gts surface reduction
    }
    else
    {
        create_surface(msurf, opt::smooth, opt::cutoff); // gts reduction
        string gsdf = msurf.pdbname + ".gts";
        FILE *fp = fopen(gsdf.c_str(), "w");
        if(!fp)
        {
            cerr << "Cannot write gts file"  << endl;
            exit(EXIT_FAILURE);
        }
        gts_surface_write (msurf.isurf, fp);
        fclose(fp);
    }

    //cerr << "debug: computing vertex neighbours" << endl;
    get_vneighbours(msurf);

    string gsdf = msurf.pdbname + "_cp.txt";
    ofstream ofs;
    ofs.open(gsdf.c_str());
    if(!ofs)
    {
        cerr << "Can't write cp file: " << gsdf << endl;
        exit(EXIT_FAILURE);
    }

    //ofs << "# " << VERSION << endl;
    //time_t now = time(0);
    //if (now != static_cast<time_t>(-1))
    //{
    //    ofs << "# " << ctime(&now);
    //}
    //ofs << endl;
    //ofs << endl;

    //write_stats(ofs, msurf.isurf);

    //ofs << "# Surface points\n";

    //cerr << "Processing ..." << endl;
    calculate_cp(msurf, opt::smooth);

    write_output(msurf, ofs);

    ofs.close();
}

int main(int argc, char** argv)
{
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

    if(opt::usage) {
        cout << opt::msg << endl;
        print_usage();
        exit(EXIT_SUCCESS);
    }

    // check there are files to process
    //cerr << "Processing: " << opt::input_file << endl;
    process_file(opt::input_file);
    
    end_time = time(NULL);    //record time that task 1 ends
    //cerr << "CalCP" << " done in a total of " << setw(4)
    //     << difftime(end_time, start_time) << " seconds\n";
    return EXIT_SUCCESS;
}
