import sys, os

def read_seed_crd(seed_file):
    file = open(seed_file, 'r')
    seed_crd = []
    i_line = 0
    for line in file:
        i_line += 1
        if((i_line % 6) == 2):
            crd = []
            crd.append(float(line.split()[0]))
            crd.append(float(line.split()[1]))
            crd.append(float(line.split()[2]))
            seed_crd.append(crd)
    file.close()
    return seed_crd

def print_seed_pdb(pdb_file, seed_crd):
    file = open(pdb_file, 'w')
    for i in range(len(seed_crd)):
        atm_num = i
        if(atm_num < 10):
            file.write("ATOM      %i  C%i  CTR A   1    %8.3f%8.3f%8.3f\n"%(atm_num,atm_num,seed_crd[i][0],seed_crd[i][1],seed_crd[i][2]))
        else:
            file.write("ATOM     %2i  C%2i CTR A   1    %8.3f%8.3f%8.3f\n"%(atm_num,atm_num,seed_crd[i][0],seed_crd[i][1],seed_crd[i][2]))   
    file.close()

def main():
    seed_file = sys.argv[1]
    pdb_file = sys.argv[2]
    seed_crd = read_seed_crd(seed_file)
    print_seed_pdb(pdb_file, seed_crd)

main()
