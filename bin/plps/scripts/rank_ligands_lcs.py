import sys,os

keywords = ['output_file', 'receptor_file', 'ligand_dir']

def read_input(input_file):
    file = open(input_file, 'r')
    lig_dir = []
    for line in file:
        key = line.split()[0]
        if(key == keywords[0]):
            output_file = line.split()[1]
        elif(key == keywords[1]):
            rec_file = line.split()[1]
        elif(key == keywords[2]):
            lig_dir.append(line.split()[1])
        elif(key not in keywords):
            sys.exit('Please enter proper parameter name in input file')
    return output_file, rec_file, lig_dir

def read_cmpd_dat(dat_file, w1, w2, w3, w4):
    file = open(dat_file, 'r')
    min_score = 99.9
    conf_idx = 0
    min_idx = 0
    for line in file:
        conf_idx += 1
        pdist = float(line.split()[1])
        appd = float(line.split()[2])
        grpd = float(line.split()[3])
        pocketsd = float(line.split()[4])
        score = w1 * pdist + w2 * appd + w3 * grpd + w4 * pocketsd
        if(min_score > score):
            min_idx = conf_idx
            min_score = score
    file.close()
    return min_idx, min_score

def write_rank(rank_file, data):
    file = open(rank_file, 'w')
    for i_data in data:
        file.write('%s %2i %9.5f\n'%(i_data[0], i_data[1], i_data[2]))
    file.close()

def main():
    if(len(sys.argv) == 2):
        input_file = sys.argv[1]
    else:
        print 'USAGE: python rank_ligands_lcs.py [input file]'
        exit(0)

    w1 = 0.6
    w2 = 0.4
    w3 = 0.7
    w4 = 0.6

    # read parameters and set variables for binary files
    rank_file, prot_ssic_file, cmpd_list = read_input(input_file)
    rec_name = prot_ssic_file[:-5]
    
    # read data file from each compound
    data = []
    for cmpd in cmpd_list:
        dat_file = '%s/%s_%s.dat'%(cmpd, rec_name, cmpd)
        min_idx, min_score = read_cmpd_dat(dat_file, w1, w2, w3, w4)
        t_data = []
        t_data.append(cmpd)
        t_data.append(min_idx)
        t_data.append(min_score)
        data.append(t_data)

    # rank compounds and write it
    data.sort(key=lambda tup:tup[2])
    write_rank(rank_file, data)

main()
    
