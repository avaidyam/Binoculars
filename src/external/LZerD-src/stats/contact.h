#ifndef _CONTACT_H_
#define _CONTACT_H_

#include "utils.h"
#include "pdb.h"
#include "align.h"
#include <algorithm>
#include <ANN/ANN.h>

void get_interface_residues(vector<atom>&, vector<atom>&);
void set_ligand_CA(map<int, int>&, vector<string>&, vector<atom>&, vector<atom>&, int&);
void check_ligand_CA(map<int, int>&);
void identify_contact_residues(vector<atom>&, vector<atom>&, ANNkd_tree*, vector<string>&);
void get_residue_list(vector<atom>&, vector<string>&);

void check_CA(map<int, int>&, int);
void set_CA(map<int, int>&, vector<atom>&, vector<atom>&, int&);
void identify_contact_residues(vector<atom>&, vector<atom>&, ANNkd_tree*, vector<string>&);



double calculate_lrms(vector<atom>&, vector<atom>&, map<int, int>&);
double calculate_irmsd(vector<atom>&, vector<atom>&, vector<atom>&,
    vector<atom>&, map<int, int>&, map<int, int>&, int, int);
double get_fnat(vector<string>&, vector<string>&);

#endif
