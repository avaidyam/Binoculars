import sys,os

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
        file.write('%s %s %2i %9.5f\n'%(i_data[0], i_data[1], i_data[2], i_data[3]))
    file.close()

def read_list(list_file):
    file = open(list_file, 'r')
    cmpd_list = []
    for line in file:
        if(line[0:4] == 'ZINC'):
            cmpd_list.append(line.split()[0])
    file.close()
    return cmpd_list

def main():
    # receive parameters from command line
    w1 = float(sys.argv[1])
    w2 = float(sys.argv[2])
    w3 = float(sys.argv[3])
    w4 = float(sys.argv[4])
    rank_file = sys.argv[5]

    # read compound list
    list_file = 'mol_list'
    cmpd_list = read_list(list_file)

    # read active compunds list
    act_list_file = 'decoy_id.lst'
    active_list = read_list(act_list_file)

    # read data file from each compound
    data = []
    for cmpd in cmpd_list:
        dat_file = '%s/%s.dat'%(cmpd, cmpd)
        min_idx, min_score = read_cmpd_dat(dat_file, w1, w2, w3, w4)
        t_data = []
        if(cmpd in active_list):
            t_data.append('act')
        else:
            t_data.append('dec')
        t_data.append(cmpd)
        t_data.append(min_idx)
        t_data.append(min_score)
        data.append(t_data)

    # rank compounds and write it
    data.sort(key=lambda tup:tup[3])
    write_rank(rank_file, data)

main()
    
