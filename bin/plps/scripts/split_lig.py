import sys,os

if(len(sys.argv) != 4):
    print 'USAGE: python prepare_ligands.py [PDB file] [Chain ID] [Ligand ID]'
    exit(0)

in_pdb = sys.argv[1]
chain_id = sys.argv[2]
lig_id = sys.argv[3]

in_file = open(in_pdb, 'r')
out_rec = open('rec.pdb', 'w')
out_lig = open('xtal-lig.pdb', 'w')

for line in in_file:
    if(line.startswith('ATOM')):
        if(line[21:22] == chain_id):
            out_rec.write(line)
    elif(line.startswith('HETATM')):
        if(line[17:20] == lig_id and line[21:22] == chain_id):
            out_lig.write(line)

in_file.close()
out_rec.close()
out_lig.close()
            
