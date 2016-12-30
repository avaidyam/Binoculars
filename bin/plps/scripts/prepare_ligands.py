import sys,os

keywords = ['PLPS_path', 'PDB2PQR_path', 'APBS_path', 'XLOGP3_path', 'ligand_file', 'BABEL_path',\
            'n_conf', 'OMEGA_path']

def read_input(input_file):
    file = open(input_file, 'r')
    lig_file = []
    for line in file:
        key = line.split()[0]
        if(key == keywords[0]):
            PLPS_dir = line.split()[1]
        elif(key == keywords[1]):
            PDB2PQR_dir = line.split()[1]
        elif(key == keywords[2]):
            APBS_dir = line.split()[1]
        elif(key == keywords[3]):
            XLOGP3_dir = line.split()[1]
        elif(key == keywords[4]):
            lig_file.append(line.split()[1])
        elif(key == keywords[5]):
            BABEL_dir = line.split()[1]
        elif(key == keywords[6]):
            n_conf = int(line.split()[1])
        elif(key == keywords[7]):
            OMEGA_dir = line.split()[1]
        elif(key not in keywords):
            sys.exit('Please enter proper parameter name in input file')
    return PLPS_dir, PDB2PQR_dir, APBS_dir, XLOGP3_dir, lig_file, BABEL_dir, n_conf, OMEGA_dir

def split_conf(mol_id):
    conf_file = '%s_omega.mol2'%(mol_id)
    file = open(conf_file, 'r')
    i_conf = 0
    for line in file:
        if(line[0:17] == '@<TRIPOS>MOLECULE'):
            i_conf += 1
            if(i_conf < 10):
                t_conf_file = '%s_conf_0%i.mol2'%(mol_id, i_conf)
            else:
                t_conf_file = '%s_conf_%i.mol2'%(mol_id, i_conf)
            t_file = open(t_conf_file, 'w')
        t_file.writelines(line)
    t_file.close()
    return i_conf

def generate_ssic(mol_id, i_conf, BABEL, PDB2PQR, script_dir, apbs_tool, APBS, bin_dir):
    if(i_conf+1 < 10):
        conf_pref = '%s_conf_0%i'%(mol_id, i_conf+1)
    else:
        conf_pref = '%s_conf_%i'%(mol_id, i_conf+1)
    file = open('%s.mol2'%(conf_pref), 'a')
    file.write('@<TRIPOS>SUBSTRUCTURE\n')
    file.write('     1 ****        1 TEMP              0 ****  ****    0 ROOT\n')
    file.close()
    os.system("sed -i 's/<0>/MOL/g' %s.mol2"%(conf_pref))
    os.system('%s -imol2 %s.mol2 -opdb %s.pdb'%(BABEL, conf_pref, conf_pref))
    os.system("sed -i 's/ATOM  /HETATM/g' %s.pdb"%(conf_pref))
    os.system('%s --ligand=%s.mol2 --ff=amber %s.pdb %s.pqr'%(PDB2PQR, conf_pref, conf_pref, conf_pref))
    convert_success = check_convert('%s.pqr'%(conf_pref))
    if(not convert_success):
        os.system('python %s/mol2topqr.py %s.mol2 %s.pqr'%(script_dir, conf_pref, conf_pref))
    os.system("sed -i 's/HETATM/ATOM  /g' %s.pdb"%(conf_pref))
    os.system("sed -i 's/HETATM/ATOM  /g' %s.pqr"%(conf_pref))
    os.system('%s/psize.py %s.pqr > %s.psize'%(apbs_tool, conf_pref, conf_pref))
    grid_pts, cntr_crd = get_grid_info('%s.psize'%(conf_pref))
    write_apbs_input(conf_pref, grid_pts, cntr_crd)
    os.system('%s %s.in'%(APBS, conf_pref))
    os.system('%s/genLocInvPocketLig -s %s_smol.dx -d %s_pot.dx -q %s.pqr -xlp %s.xlp -o %s -l %s.pdb -mol2 %s.mol2 -rad 5 -psel -ar -sa 3.0'%(bin_dir, conf_pref, conf_pref, conf_pref, mol_id, conf_pref, conf_pref, conf_pref))
    os.system('python %s/convert_seed_to_ssic.py %s.seed %s.ssic'%(script_dir, conf_pref, conf_pref))

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

def write_apbs_input(conf_pref, grid_pts, cntr_crd):
    input_file = '%s.in'%(conf_pref)
    pqr_file = '%s.pqr'%(conf_pref)
    pot_file = '%s_pot'%(conf_pref)
    surf_file = '%s_smol'%(conf_pref)
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

def check_convert(pqr_file):
    convert_success = True
    if(not os.path.isfile(pqr_file)):
        convert_success = False
    atom_exist = False
    if(convert_success):
        file = open(pqr_file, 'r')
        for line in file:
            if(line.startswith('ATOM') or line.startswith('HETATM')):
                atom_exist = True
        file.close()
    if(not atom_exist):
        convert_success = False
    return convert_success

def main():
    if(len(sys.argv) == 2):
        input_file = sys.argv[1]
    else:
        print 'USAGE: python prepare_ligands.py [input file]'
        exit(0)

    # read parameters and set variables for binary files
    PLPS_dir, PDB2PQR_dir, APBS_dir, XLOGP3_dir, lig_file, BABEL_dir, max_conf, OMEGA_dir = read_input(input_file)

    apbs_tool = PLPS_dir + '/apbs_tool'
    script_dir = PLPS_dir + '/scripts'
    bin_dir = PLPS_dir + '/bin'
    XLOGP3 = XLOGP3_dir + '/xlogp3.lnx.x86'
    OMEGA = OMEGA_dir + '/omega2'
    PDB2PQR = PDB2PQR_dir + '/pdb2pqr'
    APBS = APBS_dir + '/apbs'
    BABEL = BABEL_dir + '/babel'

    for ligand in lig_file:
        mol_id = ligand[:-5]
        os.system('%s -ewindow 15.0 -maxconfs %i -rmsrange "0.5,0.8,1.0" -rangeIncrement 5 -commentEnergy -in %s.mol2 -out %s_omega.mol2 -strictstereo false'%(OMEGA, max_conf, mol_id, mol_id))
        n_conf = split_conf(mol_id)
        os.system('%s -v %s_conf_01.mol2 %s.xlp'%(XLOGP3, mol_id, mol_id))
        for i_conf in range(n_conf):
            generate_ssic(mol_id, i_conf, BABEL, PDB2PQR, script_dir, apbs_tool, APBS, bin_dir)
        os.system('rm %s_conf*.in %s*.dx %s*.psize %s*.seed %s*.pqr %s*conf*.mol2 %s.xlp %s_omega.mol2'%(mol_id, mol_id, mol_id, mol_id, mol_id, mol_id, mol_id, mol_id))
        os.system('mkdir %s'%(mol_id))
        os.system('mv %s*.pdb %s*.ssic %s'%(mol_id, mol_id, mol_id))
    os.system('rm omega* io.mc')

main()
