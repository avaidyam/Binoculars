import sys,os

pqr_rad = {"C":1.7000, "O":1.4000, "H":1.0000, "N":1.5000, "S":1.8500, "CL":1.7500, "F":1.2000, "BR":2.5000, "Cl":1.7500, "P":1.7000, "Br":2.5000, "I":1.9800}

def read_mol2(mol2_file):
    file = open(mol2_file, 'r')
    crd = []
    atm_idx = []
    atm_type = []
    atm_name = []
    charge = []
    read_flag = 0
    for line in file:
        if(line.startswith('@<TRIPOS>BOND')):
            read_flag = 0
        if(read_flag == 1):
            tmp_crd = []
            atm_idx.append(int(line.split()[0]))
            atm_name.append(line.split()[1])
            tmp_crd.append(float(line.split()[2]))
            tmp_crd.append(float(line.split()[3]))
            tmp_crd.append(float(line.split()[4]))
            crd.append(tmp_crd)
            atm_type.append(line.split()[5])
            charge.append(float(line.split()[8]))
        if(line.startswith('@<TRIPOS>ATOM')):
            read_flag = 1
    file.close()
    return atm_idx, atm_name, crd, atm_type, charge

def assign_rad(atm_type):
    radii = []
    for i_type in atm_type:
        tmp_type = i_type.split('.')[0]
        radii.append(pqr_rad[tmp_type])
    return radii

def write_pqr(pqr_file, atm_idx, atm_name, crd, atm_type, charge, radii):
    file = open(pqr_file, 'w')
    for i in range(len(atm_idx)):
        file.write("ATOM  %5i %4s LIG     1    %8.3f%8.3f%8.3f %7.4f %6.4f\n"%(atm_idx[i], atm_name[i], crd[i][0], crd[i][1], crd[i][2], charge[i], radii[i]))
    file.write("TER\n")
    file.write("END\n")
    file.close()

def main():
    mol2_file = sys.argv[1]
    pqr_file = sys.argv[2]
    atm_idx, atm_name, crd, atm_type, charge = read_mol2(mol2_file)
    radii = assign_rad(atm_type)
    write_pqr(pqr_file, atm_idx, atm_name, crd, atm_type, charge, radii)

main()

                    

       
         
            
