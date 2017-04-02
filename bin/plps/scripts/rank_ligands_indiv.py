import sys,os
import math

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
    conf_idx = 0
    min_idx = 0
    score = []
    for line in file:
        conf_idx += 1
        pdist = float(line.split()[1])
        appd = float(line.split()[2])
        grpd = float(line.split()[3])
        pocketsd = float(line.split()[4])
        score.append(w1 * pdist + w2 * appd + w3 * grpd + w4 * pocketsd)
    file.close()
    return score

def calc_Boltzmann_avg(score, beta, rank):
    nominator = 0.0
    denominator = 0.0
    score.sort()
    if(len(score) > rank):
        for i in range(rank):
            nominator += math.exp(-1.0 * beta * score[i])
            denominator += score[i] * math.exp(-1.0 * beta * score[i])
            boltz_avg = denominator / nominator
    else:
        for i in range(len(score)):
            nominator += math.exp(-1.0 * beta * score[i])
            denominator += score[i] * math.exp(-1.0 * beta * score[i])
            boltz_avg = denominator / nominator
    return boltz_avg

def write_rank(rank_file, data):
    file = open(rank_file, 'w')
    for i_data in data:
        file.write('%s %8.5f\n'%(i_data[0], i_data[1]))
    file.close()

def main():
    if(len(sys.argv) == 2):
        input_file = sys.argv[1]
    else:
        print 'USAGE: python rank_ligands_bs.py [input file]'
        exit(0)

    # receive parameters from command line
    beta = 1
    rank = 50

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
        dat_file = '%s_%s.dat'%(rec_name, cmpd)
        score = read_cmpd_dat(dat_file, w1, w2, w3, w4)
        boltz_avg = calc_Boltzmann_avg(score, beta, rank)
        t_data = []
        t_data.append(cmpd)
        t_data.append(boltz_avg)
        data.append(t_data)

    # rank compounds and write it
    data.sort(key=lambda tup:tup[1], reverse=False)
    write_rank(rank_file, data)

main()
