import sys, os

keywords = ['PLPS_path', 'PDB2PQR_path', 'APBS_path', 'receptor_file', 'ligand_file', 'BABEL_path']

def read_input(input_file):
    file = open(input_file, 'r')
    for line in file:
        key = line.split()[0]
        if(key == keywords[0]):
            PLPS_dir = line.split()[1]
        elif(key == keywords[1]):
            PDB2PQR_dir = line.split()[1]
        elif(key == keywords[2]):
            APBS_dir = line.split()[1]
        elif(key == keywords[3]):
            rec_file = line.split()[1]
        elif(key == keywords[4]):
            lig_file = line.split()[1]
        elif(key == keywords[5]):
            BABEL_dir = line.split()[1]
        elif(key not in keywords):
            sys.exit('Please enter proper parameter name in input file')
    return PLPS_dir, PDB2PQR_dir, APBS_dir, rec_file, lig_file, BABEL_dir

def prepare_protein(prefix, ligand_file, PDB2PQR, apbs_tool, APBS, bin_dir, script_dir, BABEL):
    os.system('%s --ff=amber %s.pdb %s.pqr'%(PDB2PQR, prefix, prefix))
    os.system('%s/psize.py %s.pqr > %s.psize'%(apbs_tool, prefix, prefix))
    grid_pts, cntr_crd = get_grid_info('%s.psize'%(prefix))
    write_apbs_input(prefix, grid_pts, cntr_crd)
    os.system('%s %s.in'%(APBS, prefix))
    os.system('%s -ipqr %s.pqr -omol2 %s.mol2'%(BABEL, prefix, prefix))
    os.system('%s/genLocInvPocketProt -s %s_smol.dx -d %s_pot.dx -q %s.pqr -o %s -l %s -rad 5 -psel -ar -sa 3.0 -xlp'%(bin_dir, prefix, prefix, prefix, prefix, ligand_file))
    os.system('python %s/convert_seed_to_ssic.py %s.seed %s.ssic'%(script_dir, prefix, prefix))

def get_grid_info(psize_file):
    file = open(psize_file, 'r')
    grid_pts = []
    cntr_crd = []
    for line in file:
        if(line.startswith('Num.')):
            grid_pts.append(line.split()[5])
            grid_pts.append(line.split()[7])
            grid_pts.append(line.split()[9])
        elif(line.startswith('Center')):
            cntr_crd.append(line.split()[2])
            cntr_crd.append(line.split()[4])
            cntr_crd.append(line.split()[6])
    file.close()
    return grid_pts, cntr_crd

def write_apbs_input(prefix, grid_pts, cntr_crd):
    input_file = '%s.in'%(prefix)
    pqr_file = '%s.pqr'%(prefix)
    pot_file = '%s_pot'%(prefix)
    surf_file = '%s_smol'%(prefix)
    file = open(input_file, 'w')
    file.write('read\n')
    file.write('mol pqr %s\n'%(pqr_file))
    file.write('end\n\n')
    file.write('# ENERGY OF PROTEIN CHUNK\n')
    file.write('elec name solv\n')
    file.write('mg-manual\n')
    file.write('dime  %s %s %s\n'%(grid_pts[0], grid_pts[1], grid_pts[2]))
    file.write('grid 0.6 0.6 0.6\n')
    file.write('gcent %s %s %s\n'%(cntr_crd[0], cntr_crd[1], cntr_crd[2]))
    file.write('mol 1\n')
    file.write('lpbe\n')
    file.write('bcfl sdh\n')
    file.write('pdie 2.0\n')
    file.write('sdie 78.4\n')
    file.write('chgm spl2\n')
    file.write('srfm smol\n')
    file.write('srad 1.4\n')
    file.write('swin 0.3\n')
    file.write('sdens 10.0\n')
    file.write('temp 298.15\n')
    file.write('calcenergy total\n')
    file.write('calcforce no\n')
    file.write('write pot dx %s\n'%(pot_file))
    file.write('write smol dx %s\n'%(surf_file))
    file.write('end\n\n')
    file.write('quit\n')
    file.close()

def main():
    if(len(sys.argv) == 2):
        input_file = sys.argv[1]
    else:
        print 'USAGE: python prepare_receptor.py [input file]'
        exit(0)

    # read parameters and set variables for binary files
    PLPS_dir, PDB2PQR_dir, APBS_dir, rec_file, lig_file, BABEL_dir = read_input(input_file)

    apbs_tool = PLPS_dir + '/apbs_tool'
    script_dir = PLPS_dir + '/scripts'
    bin_dir = PLPS_dir + '/bin'
    PDB2PQR = PDB2PQR_dir + '/pdb2pqr'
    APBS = APBS_dir + '/apbs'
    BABEL = BABEL_dir + '/babel'

    rec_prefix = rec_file.split('.')[0]
    lig_prefix = lig_file.split('.')[0]
    prepare_protein(rec_prefix, lig_file, PDB2PQR, apbs_tool, APBS, bin_dir, script_dir, BABEL)
    os.system('rm %s*dx %s.seed %s.mol2 %s.pqr %s.psize %s.in io.mc'%(rec_prefix, rec_prefix, rec_prefix, rec_prefix, rec_prefix, rec_prefix))

main()
