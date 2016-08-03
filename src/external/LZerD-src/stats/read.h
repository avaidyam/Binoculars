/* prototypes for read.cc */
#ifndef _READ_H_
#define _READ_H_

#include <string>
#include <sstream>
#include <vector>
#include <iostream>
#include <fstream>

using namespace std;

#include "pdb.h"

void read_protein(string, pdb&);
void read_predictions(string, vector<vector<double> >&, double*, double*);



#endif


