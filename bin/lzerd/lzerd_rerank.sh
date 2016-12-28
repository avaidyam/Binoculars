#!/bin/sh
if [ $# -ne 4 ]; then
  echo "Usage: $0 receptor.pdb ligand.pdb lzerd_output num_predictions"
  exit 0
fi

./lzerd_pdbgen.pl $1 $2 $3 $4

./lzerd_physics_score.pl model*.pdb > $3.reranked.tmp

sort -k 2,2 -n -t "," $3.reranked.tmp > $3.reranked

rm model*.pdb $3.reranked.tmp
