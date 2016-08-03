/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 */


#ifndef _OUTPUT_H_
#define _OUTPUT_H_

#include "classes.h"
#include <fstream>
#include <iostream>
#include <iomanip>

void write_wrl(isurface&);
void write_text(isurface&, ofstream&);
void write_stats(ofstream&, GtsSurface*);

#endif
