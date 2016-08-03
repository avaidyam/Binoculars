#include <ctime>
#include <sstream>
#include <iomanip>
#include <limits>

using namespace std;

#include "read.h"
#include "contact.h"
#include "utils.h"

int main(int argc, char** argv)
{
    time_t start_time, end_time;
    start_time = time(NULL);    //record time that task begins

    if(argv[1] == NULL || argv[2]==NULL || argv[3] == NULL || argv[4] == NULL || argv[5] == NULL)
    {
        cerr << "Usage: lzerd_capri_stats bound_receptor bound_ligand unbound_receptor unbound_ligand lzerd_predictions" << endl;
        exit(EXIT_FAILURE);
    }

    string brec(argv[1]), blig(argv[2]), urec(argv[3]), ulig(argv[4]), pred(argv[5]);


    // read pdb coordinates and atom info for unbound receptor
    pdb pdb_urec;
    read_protein(urec, pdb_urec);

    // read pdb coordinates and atom info for unbound ligand
    pdb pdb_ulig;
    read_protein(ulig, pdb_ulig);

    // read coordinates of the original complex
    pdb pdb_brec, pdb_blig;
    read_protein(blig, pdb_blig);
    read_protein(brec, pdb_brec);

    vector<vector<double> > PREDS;
    double rotL[3], transL[3];
    read_predictions(pred, PREDS, transL, rotL);

    translate_atoms(pdb_ulig, transL);

    apply_rotation(pdb_ulig, rotL);

    // for the interface RMSD calculation
    get_interface_residues(pdb_brec.atoms, pdb_blig.atoms);
    // for the predicted complex, find the corresponding interface C-alpha
    map<int, int> MRES_R, MRES_L; //matching residues in the receptor and ligand
    int k1 = 0, k2 = 0;
    set_CA(MRES_R, pdb_brec.atoms, pdb_urec.atoms, k1);
    check_CA(MRES_R, 0);
    set_CA(MRES_L, pdb_blig.atoms, pdb_ulig.atoms, k2);
    check_CA(MRES_L, 1);


    // LRMSD

    vector<string> LRES;
    get_residue_list(pdb_blig.atoms, LRES);

    int k3 = 0;
    map<int, int> MRES_UL; //matching residues in ligand
    set_ligand_CA(MRES_UL, LRES, pdb_blig.atoms, pdb_ulig.atoms, k3);
    check_ligand_CA(MRES_UL);
    



    // fnat calculation
    int N = (int) pdb_brec.atoms.size();
    ANNpointArray AdataPts;
    AdataPts = annAllocPts(N, 3);

    for(int i=0; i<N; i++)
    {
        for(int j=0;j<3;j++)
            AdataPts[i][j] = pdb_brec.atoms[i].axyz[j];
    }
    ANNkd_tree *TR = new ANNkd_tree(AdataPts, N, 3);

    // get contact atoms for rec-lig
    vector<string> CBND;
    identify_contact_residues(pdb_brec.atoms, pdb_blig.atoms, TR, CBND);

    N = (int) pdb_urec.atoms.size();
    ANNpointArray BdataPts;
    BdataPts = annAllocPts(N, 3);

    for(int i=0; i<N; i++)
    {
        for(int j=0;j<3;j++)
            BdataPts[i][j] = pdb_urec.atoms[i].axyz[j];
    }
    ANNkd_tree *TUR = new ANNkd_tree(BdataPts, N, 3);

    //int CAPRI_1 = 0, CAPRI_2 = 0, CAPRI_3 = 0, CAPRI_4 = 0;


    // now do the calculations    
    for(size_t i=0;i<PREDS.size();i++)
    {
        // calculate the transformation matrix
        T43Matrix M;
        int k = 0;
        for(int l=0;l<3;l++)
        {
            for(int j=0;j<3;j++)
            {
                M[l][j] = PREDS[i][k];
                k++;
            }
        }
        M[3][0] = PREDS[i][9];
        M[3][1] = PREDS[i][10];
        M[3][2] = PREDS[i][11];
        vector<atom> atrans;
        transform(M, pdb_ulig.atoms, atrans);
    
        double irmsd = calculate_irmsd(pdb_brec.atoms, pdb_urec.atoms,
            pdb_blig.atoms, atrans, MRES_R, MRES_L, k1, k2);

        vector<string> CUNBND;
        identify_contact_residues(pdb_urec.atoms, atrans, TUR, CUNBND);
    
        double fnat = get_fnat(CBND, CUNBND);

        double lrmsd = calculate_lrms(pdb_blig.atoms, atrans, MRES_UL);

        cout << setw(10) << left << (i+1)
             << setprecision(3) << left
             << setw(10) << irmsd
             << setw(10) << lrmsd
             << setw(10) << fnat
             << endl;
    }

    // kdtree deletion
    delete TR;
    annDeallocPts(AdataPts);
    delete TUR;
    annDeallocPts(BdataPts);


    annClose();

    end_time = time(NULL);    //record time that task 1 ends
    cerr << "lzerd_stats done in a total of " << setw(4)
         << difftime(end_time, start_time) << " seconds\n";

    exit(EXIT_SUCCESS);
}
