#!/usr/bin/perl
if($#ARGV + 1 < 4)
{
  print "Generare num_predictions PDB files where each is composed of " .
        "receptor.pdb + a transformed ligand.\n";
  print "\nUsage: lzerd_pdbgen.pl receptor.pdb ligand.pdb lzerd_output num_predictions\n";
}
else
{
  $receptor = $ARGV[0];
  $ligand = $ARGV[1];
  $lzerd_output = $ARGV[2];
  $num_predictions = $ARGV[3];

  `./PDBGEN $receptor $ligand $lzerd_output $num_predictions`;

  foreach $pred_number(1 .. $num_predictions) {
    `grep ^ATOM $receptor > model$pred_number.pdb`;
    `echo TER >> model$pred_number.pdb`;
    `cat ligand$pred_number.pdb >> model$pred_number.pdb`;
    `rm ligand$pred_number.pdb`;
  }
}
