/**
 *  @author Vishwesh Venkatraman
 *  @date   Sep/27/2007
 *
 * Routines for writing vrml and text ouput
 */
 

#include "output.h"
#include "constants.h"
#include "options.h"
#include "surface.h"
 

#include <valarray>



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


const string VRML2_SHAPE =
"Shape {\n"
"  appearance Appearance {\n"
"    material DEF mat Material { transparency 0.0 }\n"
"  }\n"
"  geometry IndexedFaceSet {\n"
"    solid FALSE\n"
"    creaseAngle 0.5 #???\n";

const string VRML2_XYZ_HEADER =
"    coord Coordinate {\n"
"      point [\n";

const string VRML2_XYZ_FOOTER =
"      ]\n"
"    }\n";

const string VRML2_RGB_HEADER =
"    color Color {\n"
"      color [\n";

const string VRML2_RGB_FOOTER =
"      ]\n"
"    }\n";
const string VRML2_IJK_HEADER =
"    coordIndex [\n";

const string VRML2_IJK_FOOTER =
"    ]\n"
"  }\n"
"}\n"
"\n";


void write_wrl_head (ofstream& ofs)
{
    ofs << VRML2_HEADER 
        << endl
        << "# " << VERSION << endl;

    time_t now = time(0);

    if (now != static_cast<time_t>(-1))
    {
        ofs << "# " << ctime(&now);
    }

    ofs << endl
        << VRML2_BACKGROUND
        << endl
        << VRML2_NAVINFO
        << endl;

    ofs << endl
        << VRML2_SHAPE
        << VRML2_XYZ_HEADER; 

}

void write_vrml_vertex(GtsPoint* point, gpointer* data){
    ofstream *ofs = static_cast<ofstream *> (data[1]);
    *ofs << fixed
        << setprecision(4)
        << setw(10) << point->x
        << setw(10) << point->y
        << setw(10) << point->z
        << ","
        << endl;
    

    GTS_OBJECT (point)->reserved = GUINT_TO_POINTER ((*((guint *) data[0]))++);
}


void write_vrml_face(GtsTriangle * t, gpointer* data){
    GtsVertex * v1, * v2, * v3;
    gts_triangle_vertices (t, &v1, &v2, &v3);

    ofstream *ofs = static_cast<ofstream *> (data[1]);
    
    *ofs << setw(6) << GPOINTER_TO_UINT (GTS_OBJECT (v1)->reserved)
        << ","
        << setw(6) << GPOINTER_TO_UINT (GTS_OBJECT (v2)->reserved)
        << ","
        << setw(6) << GPOINTER_TO_UINT (GTS_OBJECT (v3)->reserved)
        << ", -1,"
        << endl;
}


void write_wrl_surface (ofstream& ofs, isurface& msurf)
{
    gpointer data[2];
    guint n = 0;
    data[0] = &n;
    data[1] = &ofs;
    gts_surface_foreach_vertex (msurf.isurf, (GtsFunc) write_vrml_vertex, data);

    ofs << VRML2_XYZ_FOOTER;
    int nv = gts_surface_vertex_number(msurf.isurf);
    ofs << VRML2_RGB_HEADER;
    for(int i=0;i<nv;i++)
    {
        ofs << fixed
            << setprecision(2)
            << setw(6) << 1.00000
            << setw(6) << 1.00000
            << setw(6) << 1.00000
            << ","
            << endl;
    }
    ofs << VRML2_RGB_FOOTER;
    ofs << VRML2_IJK_HEADER;
    gts_surface_foreach_face (msurf.isurf, (GtsFunc) write_vrml_face, data);

    ofs << VRML2_IJK_FOOTER;
}


void
write_wrl_cpts (ofstream& ofs, isurface& msurf) 
{
    bool blueball_defined = false;

    for(size_t i=0;i<msurf.RVCP.size();i++)
    {
        ofs << "Transform {\n"
            << "  translation "
            << msurf.RVCP[i].coords[0]
            << " "
            << msurf.RVCP[i].coords[1]
            << " "
            << msurf.RVCP[i].coords[2]
            << "\n";

        if(!blueball_defined)
        {
            blueball_defined = true;
            ofs << "  children DEF BlueBall\n"
                << "    Shape {\n"
                << "      appearance Appearance {\n"
                << "         material Material { diffuseColor 0 0 1 }\n"
                << "      }\n"
                << "      geometry Sphere { radius 0.2 }\n"
                << "    }\n"
                << "}\n";
        }
        else
        {
            ofs << "  children USE BlueBall\n"
                << "}\n";
        }
    }
}


 
void write_wrl(isurface& msurf)
{
    if(!opt::wrl) return;
    string gfx = msurf.pdbname;
    gfx += ".wrl";

    ofstream ofs;
    ofs.open(gfx.c_str());
    if(!ofs)
    {
        cerr << "Can't write wrl file: " << gfx << endl;
        exit(EXIT_FAILURE);
    }

    write_wrl_head(ofs);

    write_wrl_surface(ofs, msurf);
    write_wrl_cpts(ofs, msurf);

    ofs.close();
}



void write_stats(ofstream& ofs, GtsSurface* s)
{
    GtsSurfaceStats stats;
    GtsSurfaceQualityStats qstats;

    gts_surface_stats (s, &stats);
    gts_surface_quality_stats (s, &qstats);

    ofs << "# vertices: " << stats.edges_per_vertex.n
        << " edges: " << stats.faces_per_edge.n
        << " faces: " << stats.n_faces
        << endl;
    ofs << "# Geometric statistics" << endl;
    ofs << right << fixed << setprecision(4);
    ofs << "#   face quality: "
        << "min: " << setw(6) << qstats.face_quality.min
        << " mean: " << setw(6) << qstats.face_quality.mean
        << " stddev: " << setw(6) << qstats.face_quality.stddev
        << " | max: " << setw(6) << qstats.face_quality.max
        << endl;
    ofs << "#      face area: "
        << "min: " << setw(6) << qstats.face_area.min
        << " mean: " << setw(6) << qstats.face_area.mean
        << " stddev: " << setw(6) << qstats.face_area.stddev
        << " | max: " << setw(6) << qstats.face_area.max
        << endl;
    ofs << "#    edge length: "
        << "min: " << setw(6) << qstats.edge_length.min
        << " mean: " << setw(6) << qstats.edge_length.mean
        << " stddev: " << setw(6) << qstats.edge_length.stddev
        << " | max: " << setw(6) << qstats.edge_length.max
        << endl;
    ofs << endl;

    ofs << "# Volume: " << fabs(gts_surface_volume(s)) << endl;
    ofs << "# Area  : " << gts_surface_area(s) << endl;
    ofs << endl;

}

void write_text(isurface& msurf, ofstream& ofs)
{
    for(size_t i=0;i<msurf.RVCP.size();i++)
    {
        ofs << left << setw(5) <<  i+1;
        ofs << setw(4) << msurf.RVCP[i].ctype;
        ofs << right << fixed << setprecision(4)
            << setw(10) << msurf.RVCP[i].coords[0]
            << setw(10) << msurf.RVCP[i].coords[1]
            << setw(10) << msurf.RVCP[i].coords[2]
            << setw(10) << msurf.RVCP[i].normal[0]
            << setw(10) << msurf.RVCP[i].normal[1]
            << setw(10) << msurf.RVCP[i].normal[2]
            << setw(10) << msurf.RVCP[i].CURV
            << setw(10) << msurf.RVCP[i].MEP
            << setw(12) << msurf.RVCP[i].nres
            << endl;
    }

    ofs << endl;
}

