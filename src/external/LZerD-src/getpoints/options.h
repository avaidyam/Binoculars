/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 *  Routines for checking and parsing command line options
 */


#ifndef _CALCP_OPTIONS_H_
#define _CALCP_OPTIONS_H_

#include <string>
#include <vector>

using namespace std;

namespace opt 
{
    extern bool usage;
    extern bool version;
    extern bool debug;
    extern string msg;
    extern double smooth;	

    extern string input_file;//pqr file

    extern bool wrl;

    extern string gts_file; // when previously calculated surface is provided
    extern bool have_surf;
    extern double cutoff;

    extern void DefaultOptions();
    extern void ReadCommandLine(int argc, char** argv);
    extern void Check();
}

#endif 
