import sys,os

def read_seed_file(seed_file):
    file = open(seed_file, 'r')
    seed_crd = []
    shape = []
    esp = []
    hb = []
    xlp = []
    geo_hist = []
    for line in file:
        if(line.startswith('Printing')):
            n_seed = int(line.split()[3])
        elif(line.startswith('DbtwSeeds:')):
            geo_hist_flag = True
        elif(line.startswith('HBDA:')):
            geo_hist_flag = False
        elif(line.startswith('EXYZ')):
            tmp_crd = []
            tmp_crd.append(float(line.split()[1]))
            tmp_crd.append(float(line.split()[2]))
            tmp_crd.append(float(line.split()[3]))
            seed_crd.append(tmp_crd)
        elif(line[0:5] == '	0 72'):
            tmp_zerd = []
            for i in range(len(line.split()) - 2):
                tmp_zerd.append(float(line.split()[i+2]))
            shape.append(tmp_zerd)
        elif(line[0:6] == '	3 144'):
            tmp_zerd = []
            for i in range(len(line.split()) - 2):
                tmp_zerd.append(float(line.split()[i+2]))
            esp.append(tmp_zerd)
        elif(line[0:6] == '	5 144'):
            tmp_zerd = []
            for i in range(len(line.split()) - 2):
                tmp_zerd.append(float(line.split()[i+2]))
            hb.append(tmp_zerd)
        elif(line[0:6] == '	6 144'):
            tmp_zerd = []
            for i in range(len(line.split()) - 2):
                tmp_zerd.append(float(line.split()[i+2]))
            xlp.append(tmp_zerd)
        elif(line.split()[0] == str(n_seed) and geo_hist_flag):
            tmp_geo = []
            for i in range(len(line.split())):
                tmp_geo.append(int(line.split()[i]))
            geo_hist.append(tmp_geo)
    file.close()
    return n_seed, seed_crd, shape, esp, hb, xlp, geo_hist

def get_norm(zerd_vec):
    sqr_sum = 0.0
    for comp in zerd_vec:
        sqr_sum += comp ** 2.0
    norm = sqr_sum ** 0.5
    return norm

def normalize_zerd(zerd_mat):
    norm_zerd_mat = []
    for zerd_vec in zerd_mat:
        norm = get_norm(zerd_vec)
        norm_zerd_vec = []
        for comp in zerd_vec:
            if(norm == 0.0):
                norm_zerd_vec.append(0.0)
            else:
                norm_zerd_vec.append(comp / norm)
        norm_zerd_mat.append(norm_zerd_vec)
    return norm_zerd_mat

def write_ssic_file(ssic_file, n_seed, seed_crd, shape, esp, hb, xlp, geo_hist):
    file = open(ssic_file, 'w')
    file.write('%i 72 144 144 144\n'%(n_seed))
    for i_seed in range(n_seed):
        file.write('%8.3f %8.3f %8.3f\n'%(seed_crd[i_seed][0], seed_crd[i_seed][1], seed_crd[i_seed][2]))
        file.write(' 0 72')
        for i_comp in range(72):
            if(i_comp == 71):
                file.write(' %7.5f\n'%(shape[i_seed][i_comp]))
            else:
                file.write(' %7.5f'%(shape[i_seed][i_comp]))
        file.write(' 3 144')
        for i_comp in range(144):
            if(i_comp == 143):
                file.write(' %7.5f\n'%(esp[i_seed][i_comp]))
            else:
                file.write(' %7.5f'%(esp[i_seed][i_comp]))
        file.write(' 5 144')
        for i_comp in range(144):
            if(i_comp == 143):
                file.write(' %7.5f\n'%(hb[i_seed][i_comp]))
            else:
                file.write(' %7.5f'%(hb[i_seed][i_comp]))
        file.write(' 6 144')
        for i_comp in range(144):
            if(i_comp == 143):
                file.write(' %7.5f\n'%(xlp[i_seed][i_comp]))
            else:
                file.write(' %7.5f'%(xlp[i_seed][i_comp]))
        for i_comp in range(len(geo_hist[i_seed])):
            if(i_comp == (len(geo_hist[i_seed]) - 1)):
                file.write(' %i\n'%(geo_hist[i_seed][i_comp]))
            elif(i_comp == 0):
                file.write('%i'%(geo_hist[i_seed][i_comp]))
            else:
                file.write(' %i'%(geo_hist[i_seed][i_comp]))
    file.close()

def main():
    seed_file = sys.argv[1]
    ssic_file = sys.argv[2]
    n_seed, seed_crd, shape, esp, hb, xlp, geo_hist = read_seed_file(seed_file)
    norm_shape = normalize_zerd(shape)
    norm_hb = normalize_zerd(hb)
    write_ssic_file(ssic_file, n_seed, seed_crd, norm_shape, esp, norm_hb, xlp, geo_hist)

main()
            
