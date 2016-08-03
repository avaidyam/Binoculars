#!/bin/sh
if [ $# -ne 2 ]; then
    echo 1>&2 Usage: $0 receptor.pdb ligand.pdb
    exit 127
fi

#store arguments
receptor=$1
ligand=$2
rec_name=`basename $receptor .pdb`
lig_name=`basename $ligand .pdb`

# convert to pdb.ms
rec_ms=$rec_name".pdb.ms"
./mark_sur $receptor $rec_ms
lig_ms=$lig_name".pdb.ms"
./mark_sur $ligand $lig_ms


#get cp; run GETPOINTS
echo "Calculating surfaces ..."
./GETPOINTS -pdb $rec_ms -smooth 0.35 -cut 1e-04
./GETPOINTS -pdb $lig_ms -smooth 0.35 -cut 1e-04

#get ZINV; run LZD
echo "Calculating Zernike ..."
rec_cp=$rec_name"_cp.txt"
lig_cp=$lig_name"_cp.txt"
rec_gts=$rec_name".gts"
lig_gts=$lig_name".gts"
rec_inv=$rec_name"_01.inv"
lig_inv=$lig_name"_01.inv"
./LZD32 -g $rec_gts -c $rec_cp -o $rec_name -dim 161 -rad 6.0 -ord 10
./LZD32 -g $lig_gts -c $lig_cp -o $lig_name -dim 161 -rad 6.0 -ord 10
rm *.dx *.grid vecCP.txt

#run LZerD
echo "LZerD ..."
outfile=$rec_name"_"$lig_name".out"
./LZerD -rec $rec_cp -lig $lig_cp -prec $rec_ms -plig $lig_ms -zrec $rec_inv -zlig $lig_inv -rfmin 4.0 -rfmax 9.0 -rfpmax 15.0 -nvotes 8 -cor 0.7 -dist 2.0 -nrad 2.5 > $outfile

#edit the 4 argument to output number of orientations
#echo "Outputing top ranked results"
#./PDBGEN $receptor $ligand $outfile 3

#rm *.inv *.txt *.out *.ms *.gts

