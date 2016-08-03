/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 */


#ifndef _READ_H_
#define _READ_H_

#include "classes.h"
#include <iostream>
#include <fstream>

const char kBlankChars[] = " \t\n\r";

void read_pdb(string, isurface&);
string get_file_name(const string&);
void read_pqr(string, isurface&);
void calculate_sas(vector<atom>&, int&);

#endif
