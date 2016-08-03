#ifndef _UTILS_H_
#define _UTILS_H_

#include <string>
#include <cmath>
#include <vector>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <iostream>
#include <set>
#include <cstdlib>

using namespace std;

#include "pdb.h"

typedef double T43Matrix[4][3];

const char kBlankChars[] = " \t\n\r";

string trimmed(string const&, char const*);
string rtrimmed(string const&, char const*);
string ltrimmed(string const&, char const*);

double get_distance(double *, double *, int);
double get_distance2(double *, double *, int);

string basename(const string&);
string get_file_name(const string&);

void get_transformation_matrix(double*, double*, T43Matrix);
void transform(T43Matrix, vector<atom>&, vector<atom>&);

void apply_rotation(pdb&, double*);
void translate_atoms(pdb&, double*);

void print_complex(int, vector<atom>&, vector<atom>&);
string to_string(int);


#endif
