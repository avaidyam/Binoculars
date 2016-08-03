/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 *  Routines for checking and parsing command line options
 */


#ifndef _DOCK_OPTIONS_H_
#define _DOCK_OPTIONS_H_

#include <string>
#include <vector>

using namespace std;

namespace opt 
{
    extern bool usage;
    extern bool version;

    extern string rec_file;
    extern string lig_file;
    extern string zrinv_file;
    extern string zlinv_file;
    extern string pdbrec_file;
    extern string pdblig_file;
    extern string intrec_file;
    extern string intlig_file;
    extern string output_filename;


    extern string msg;

    extern double corr_cutoff;
    extern double rf_min_dist;
    extern double rf_max_dist;
    extern double rfdist_cutoff;
    extern double fpointdist_cutoff; // distance cutoff for the 4 the point

    extern int nvotes;
    extern double nradius;

    extern string msg;
    extern bool applyrandom;


    extern void DefaultOptions();
    extern void ReadCommandLine(int argc, char** argv);
    extern void Check();
}

#endif 
