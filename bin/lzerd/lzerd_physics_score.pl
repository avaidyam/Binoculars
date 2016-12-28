#!/usr/bin/perl
if($#ARGV + 1 < 1)
{
  print "Given a series of PDB files applies the physics based scoring function\n";
  print "\nUsage: lzerd_physics_score.pl <PDB file1> <PDB file2> ...\n";
}
else
{
  for $filename(@ARGV)
  {
    $basename = `basename $filename`;
    chomp($basename);
    $pdb_extension_index = rindex($basename, '.');

    $prefix = substr($basename, 0, $pdb_extension_index);
                          
    `./hbplus -o $filename ; ./modify_hbplus $prefix.h $prefix.pdb.h ; rm $prefix.h $prefix.hb2`;
    print `./score --decoy $prefix.pdb.h --weights all | grep -v ^Score | awk '{print "$basename,"\$0}'`;
    `rm $prefix.pdb.h`;
  }
}
