import sys,os

keywords = ['PLPS_path', 'receptor_file']

def read_input(input_file):
    file = open(input_file, 'r')
    for line in file:
        key = line.split()[0]
        if(key == keywords[0]):
            PLPS_dir = line.split()[1]
        elif(key == keywords[1]):
            rec_file = line.split()[1]
        elif(key not in keywords):
            sys.exit('Please enter proper parameter name in input file')
    return PLPS_dir, rec_file

def get_np_patch(prot_ssic_file):
    file = open(prot_ssic_file, 'r')
    for line in file:
        np_patch = int(line.split()[0])
        break
    return np_patch

def summarize_log(log_file):
    file = open(log_file, 'r')
    sum_pdist = 0.0
    sum_appd = 0.0
    n_pair = 0
    zero_patch = False
    for line in file:
        if(line.startswith('SUM1:')):
            avg_pdist = 99.99
            avg_appd = 99.99
            grpd = 99.99
            n_pair = 0
            zero_patch = True
            break
        if(line.startswith('SUM:')):
            grpd = float(line[30:37]) / 20.0
        else:
            sum_pdist +=  float(line[13:19])
            sum_appd += float(line[20:26])
            n_pair += 1
    file.close()
    if(not zero_patch):
        avg_pdist = sum_pdist / float(n_pair)
        avg_appd = sum_appd / float(n_pair)
    return avg_pdist, avg_appd, grpd

def calc_pocket_sd(prot_patch, lig_patch):
    pocket_sd = float(prot_patch) - float(lig_patch)
    pocket_sd = abs(pocket_sd) / float(prot_patch)
    return pocket_sd

def write_summary(summary_file, data):
    file = open(summary_file, 'w')
    for t_data in data:
        file.write('%2i %8.5f %8.5f %8.5f %8.5f\n'%(t_data[0], t_data[1], t_data[2], t_data[3], t_data[4]))
    file.close()

def main():
    if(len(sys.argv) == 5):
        input_file = sys.argv[1]
        mol_id = sys.argv[2]
        max_conf = int(sys.argv[3])
        log_prefix = sys.argv[4]
    else:
        print 'USAGE: python compare_seeds.py [input file] [lig dir] [n conf] [log prefix]'
        exit(0)

    # read parameters and set variables for binary files
    PLPS_dir, prot_ssic_file = read_input(input_file)
    
    bin_dir = PLPS_dir + '/bin'
    stp_mol_id = os.path.basename(mol_id.rstrip(os.sep))

    rec_pref = prot_ssic_file.split('.')[0]
    np_patch = get_np_patch(prot_ssic_file)
    if(np_patch < 40):
        weights = '0.65 0.50 0.95'
    else:
        weights = '0.30 0.90 0.75'

    data = []
    for i in range(max_conf):
        if(i < 9):
            lig_ssic_file = '%s/%s_conf_0%i.ssic'%(mol_id, stp_mol_id, i+1)
            log_file = '%s/%s/%s_%s_conf_0%i.dat'%(log_prefix, stp_mol_id, rec_pref, stp_mol_id, i+1)
        else:
            lig_ssic_file = '%s/%s_conf_%i.ssic'%(mol_id, stp_mol_id, i+1)
            log_file = '%s/%s/%s_%s_conf_%i.dat'%(log_prefix, stp_mol_id, rec_pref, stp_mol_id, i+1)
        if(not os.path.isfile(lig_ssic_file)):
            break
        os.system('%s/CompareSeed %s %s 99 %s > %s'%(bin_dir, prot_ssic_file, lig_ssic_file, weights, log_file))
        lig_patch = get_np_patch(lig_ssic_file)
        pdist, appd, grpd = summarize_log(log_file)
        pocket_sd = calc_pocket_sd(np_patch, lig_patch)
        t_data = []
        t_data.append(i+1)
        t_data.append(pdist)
        t_data.append(appd)
        t_data.append(grpd)
        t_data.append(pocket_sd)
        data.append(t_data)
    summary_file = '%s/%s_%s.dat'%(os.getcwd(), rec_pref, stp_mol_id)
    write_summary(summary_file, data)
        
main()
