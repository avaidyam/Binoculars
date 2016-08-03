#ifndef _CONSTANTS_H_
#define _CONSTANTS_H_

#include <string>
#include <cmath>
using namespace std;

const string APPNAME = "LZerD";
const string VERSION = "5.0";
const string COPYRIGHT = "Vishwesh Venkatraman";
const double ANGLE_BETWEEN_NORMALS = 1.2; // 75deg
const double TORSION_ANGLE_CONSTRAINT = 1.2; // 80 deg
const double MIN_REFERENCE_AXIS_ANGLE_CONSTRAINT = (5./18.)*M_PI; // 50
const double MAX_REFERENCE_AXIS_ANGLE_CONSTRAINT = (7./9.)*M_PI; // 140
const double REFERENCE_AXIS_ANGLE_CONSTRAINT = 1.2;//1.2
const double PAIRWISE_NORMAL_ANGLE_CUTOFF = 0.8; // 45deg , 0.8
const double SUM_ANGLE_TOL = 1.2;//75deg
const double REF_DIFF_ANGLE_TOL = 0.75;//45deg


const string VRML2_HEADER =
"#VRML V2.0 utf8\n";

const string VRML2_BACKGROUND =
"Background {\n"
"  skyColor 1.0 1.0 1.0\n"
"}\n";

const string VRML2_NAVINFO = 
"NavigationInfo {\n"
"  type [\"EXAMINE\",\"ANY\"]\n"
"}\n";


#endif
