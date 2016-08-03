/*=============================================================================
 $Id: sas.c,v 1.5 2007/03/27 15:38:27 jkleinj Exp $
POPS-A : Parameter OPtimised Surfaces at atomic resolution.
         F. Fraternali and L. Cavallo (2002)
         Nucleic Acids Res. 30(13), 2950-2960.

C version

Copyright (C) 2002-2007 Franca Fraternali, program author
Copyright (C) 2002-2007 Luigi Cavallo, program author
Copyright (C) Kuang Lin (translation of original Fortran code to C, 07.08.2002)
Copyright (C) 2007 Jens Kleinjung, code support

License:
    This program is free software; you can redistribute it and/or modify                                                                       
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
=============================================================================*/

#include "read.h"
#include "sasdata.h"

// SAS_rNames = residue name to which the atom belongs
// SAS_nRes = number of residues in the molecule
// SAS_nAt = number of atoms in the molecule
// SAS_rNum   = residue number to which the atom belongs

void calculate_sas(vector<atom>& A, int& n)
{
    assign_param(A);
    get_topology();
    calc_mol_sasa();
    assign_areas(A, n);
}

/* assign to each atom in the pdb file the radius and the param */
void assign_param(vector<atom>& A)
{
    int key, key_all = 0;
    
    int j = 1;
    int i3 = 0;
    
    for(size_t i=0;i<A.size();i++)
    {
        if(A[i].atype.at(0) == 'H') continue; 
        SAS_aNum[j] = A[i].anum;  /* atom number */
        strcpy(SAS_aNames[j], A[i].atype.c_str());
        strcpy(SAS_rNames[j], A[i].residue.c_str());
		SAS_rNum[j] = A[i].rnum; /* residue number */
 		/* initialize the arrays of coordinates */
		SAS_aCoord[i3+1] = A[i].axyz[0]; 
        SAS_aCoord[i3+2] = A[i].axyz[1];
		SAS_aCoord[i3+3] = A[i].axyz[2];

		j = j+1;
		i3 = i3+3;
    }
	
	SAS_nAt = j-1;  /* total number of atoms */
 
    /* assign to each atom in the pdb file the radius and the param */
    key_all = 0;
    for(int i=1;i<=SAS_nAt; i++)
	{ 
		key=0;
		for(j = 1;j <= 21; j++)
		{
			if(strcmp(SAS_rNames[i], SAS_RTYPES[j]) == 0)
			{
				for(int k=0;k<SAS_NUM_ATYPES;k++) 
				{
					if(strcmp(SAS_aNames[i], SAS_ATYPES[k]) == 0)
					{
						SAS_rType[i] = j;
						SAS_aType[i] = k;
						SAS_akPol[i] = SAS_AKPOL[j][k];
						key = 1;
						break;
					}
				} /* end for */
				break;
			}
		}/* end for */
 
		if (key == 0)
		{
			printf("params not found for atom %d %s \n", i, SAS_aNames[i]);
			if (SAS_k_fast)
			{
				exit(EXIT_FAILURE);
			}
			key_all=1;
		}
	}/* end for */
	
    if(key_all == 1)
    {
      exit(EXIT_FAILURE); /* failed to find some key type */
    }
}

/*___________________________________________________________________________*/
/* get the topology */
void get_topology()
{
    int i,j,i3,j3,n;
    double dx,dy,dz;
    double rr;
    double cutoff;
    double radAtmi,radAtmj; /* radius of atom i and j */
    
    int klj;
    int keybond,key;
    
    double r_cut; /* 2 atoms bonded if dist =< 0.5*radAtmi*radAtmj */
    /* calculate bonds.  */
    SAS_nBon=0;
    for(i=1;i<=SAS_nAt-1;i++)
    {
        for(j=i+1;j<=SAS_nAt;j++)
        {
            i3=(i-1)*3;
            j3=(j-1)*3;

            dx = SAS_aCoord[i3+1] - SAS_aCoord[j3+1];
            dy = SAS_aCoord[i3+2] - SAS_aCoord[j3+2];
            dz = SAS_aCoord[i3+3] - SAS_aCoord[j3+3];
            rr = sqrt (dx*dx + dy*dy + dz*dz);
	  
	        radAtmi = SAS_ARADIUS[SAS_rType[i]][SAS_aType[i]];
            radAtmj = SAS_ARADIUS[SAS_rType[j]][SAS_aType[j]];
	        r_cut = 0.5 * (radAtmi + radAtmj);

	        if(rr < r_cut)
            {
                keybond = 1;
                if(SAS_rNum[i] != SAS_rNum[j])
                {
		             keybond = 0;
		        }
                if(SAS_rNum[j] == SAS_rNum[i]+1)
                {
		             keybond = 1;
		        }
                if(keybond == 1)
                {
                    SAS_nBon ++;
                    SAS_ib[SAS_nBon] = i;
                    SAS_jb[SAS_nBon] = j;
                    if(!SAS_k_fast)
                    {
		               if(rr<0.5)
                       {
			                 printf(" Warning, atoms %d %d Too close\n",i,j);
		               }
		            }
                }
            }
        }/* end for */
    }/* end for */
	

    if(SAS_nBon> SAS_maxBonds)
    {
        printf(" Too many bonds ... Max = %d \n",SAS_maxBonds);
        exit(EXIT_FAILURE);
    }
    
	/*___________________________________________________________________________*/
    /*  calculate now angles from bonds */
    SAS_nAng = 0;
    for(i = 1;i<=SAS_nBon-1;i++)
    {
        for(j = i+1;j<=SAS_nBon;j++)
        {
            if(SAS_ib[i] == SAS_ib[j])
            {
                SAS_nAng ++;
		        SAS_it[SAS_nAng] = SAS_jb[i];
                SAS_jt[SAS_nAng] = SAS_ib[i];
                SAS_kt[SAS_nAng] = SAS_jb[j];
            }
            if(SAS_ib[i] == SAS_jb[j])
            {
                SAS_nAng ++;
                SAS_it[SAS_nAng] = SAS_jb[i];
                SAS_jt[SAS_nAng] = SAS_ib[i];
                SAS_kt[SAS_nAng] = SAS_ib[j];
            }
            if(SAS_jb[i] == SAS_ib[j])
            {
                SAS_nAng ++;
                SAS_it[SAS_nAng] = SAS_ib[i];
                SAS_jt[SAS_nAng] = SAS_jb[i];
                SAS_kt[SAS_nAng] = SAS_jb[j];
            }
            if(SAS_jb[i] == SAS_jb[j])
            {
                SAS_nAng ++;
                SAS_it[SAS_nAng] = SAS_ib[i];
                SAS_jt[SAS_nAng] = SAS_jb[i];
                SAS_kt[SAS_nAng] = SAS_ib[j];
            }
        }
    }
    if(SAS_nAng > 3*SAS_maxBonds)
    {
        printf(" Too many angles .... Max = %d \n",3*SAS_maxBonds);
        exit(EXIT_FAILURE);
    }
    
	/*___________________________________________________________________________*/
    /* calculate now torsions from angles     */
    SAS_nTor = 0;
    for(i = 1;i<=SAS_nAng-1;i++)
    {
        for(j = i+1;j<=SAS_nAng;j++)
        {
            /* check that atoms i & j are not already forming a torsion 
				this check needed to account for rings */
            if(SAS_it[i] == SAS_jt[j] && SAS_jt[i] == SAS_it[j])
            {
                key = 0;
                for(n = 1;n<=SAS_nAng;n++)
                {
                    if((SAS_kt[j] == SAS_it[n] && SAS_kt[i] == SAS_kt[n]) ||
                        (SAS_kt[i] == SAS_it[n] && SAS_kt[j] == SAS_kt[n]))
					{
                        key = 1;
					}
                } 
                for(n = 1;n<=SAS_nTor;n++)
                {
                    if ((SAS_kt[j] == SAS_ip[n] && SAS_kt[i] == SAS_lp[n]) ||
                        (SAS_kt[i] == SAS_ip[n] && SAS_kt[j] == SAS_lp[n]))
					{
                        key = 1;
					}
                }
                if(key == 0)
                {
                    SAS_nTor ++;
                    SAS_ip[SAS_nTor] = SAS_kt[j];
                    SAS_jp[SAS_nTor] = SAS_it[i];
                    SAS_kp[SAS_nTor] = SAS_jt[i];
                    SAS_lp[SAS_nTor] = SAS_kt[i];
                }
            }
            /* 2 */
            if((SAS_it[i] == SAS_jt[j]) && (SAS_jt[i] == SAS_kt[j]))
            {
                key = 0;
                for(n = 1;n<=SAS_nAng;n++)
                {
                    if((SAS_it[j] == SAS_it[n] && SAS_kt[i] == SAS_kt[n]) ||
                        (SAS_kt[i] == SAS_it[n] && SAS_it[j] == SAS_kt[n]))
                    {
		                key = 1;
		            }
                }
                for(n = 1;n<=SAS_nTor;n++)
                {
                    if ((SAS_it[j] == SAS_ip[n] && SAS_kt[i] == SAS_lp[n])  || 
                        (SAS_kt[i] == SAS_ip[n] && SAS_it[j] == SAS_lp[n])) 
                    {
                        key = 1;
		            }
                }
                if(key == 0)
                {
                    SAS_nTor ++;
                    SAS_ip[SAS_nTor] = SAS_it[j];
                    SAS_jp[SAS_nTor] = SAS_it[i];
                    SAS_kp[SAS_nTor] = SAS_jt[i];
                    SAS_lp[SAS_nTor] = SAS_kt[i];
                }
            }
            /* 3 */
            if(SAS_kt[i] == SAS_jt[j] && SAS_jt[i] == SAS_it[j])
            {
                key = 0;
                for(n = 1;n<=SAS_nAng;n++)
                {
                    if((SAS_it[i] == SAS_it[n] && SAS_kt[j] == SAS_kt[n])  || 
                        (SAS_kt[j] == SAS_it[n] && SAS_it[i] == SAS_kt[n]))
                    {
                        key = 1;
		            }
                }
                for(n = 1;n<=SAS_nTor;n++)
                {
		            if((SAS_it[i] == SAS_ip[n] && SAS_kt[j] == SAS_lp[n])  || 
		              (SAS_kt[j] == SAS_ip[n] && SAS_it[i] == SAS_lp[n]))
                    {
		                key = 1;
		            }
                }
                if(key == 0)
                {
                    SAS_nTor ++;
                    SAS_ip[SAS_nTor] = SAS_it[i];
                    SAS_jp[SAS_nTor] = SAS_jt[i];
                    SAS_kp[SAS_nTor] = SAS_kt[i];
                    SAS_lp[SAS_nTor] = SAS_kt[j];
                }
            }
            /* 4 */
            if(SAS_kt[i] == SAS_jt[j]  && SAS_jt[i] == SAS_kt[j])
            {
                key = 0;
                for(n = 1;n<=SAS_nAng;n++)
                {
		             if((SAS_it[i] == SAS_it[n] && SAS_it[j] == SAS_kt[n]) || 
		                (SAS_it[j] == SAS_it[n] && SAS_it[i] == SAS_kt[n]))
                     { 
		                 key = 1;
		             }
                }
                for(n = 1;n<=SAS_nTor;n++)
                {
		            if((SAS_it[i] == SAS_ip[n] && SAS_it[j] == SAS_lp[n]) || 
		              (SAS_it[j] == SAS_ip[n] && SAS_it[i] == SAS_lp[n]))
                    {
                        key = 1;
		            }
                }
                if(key == 0)
                {
                    SAS_nTor ++;
                    SAS_ip[SAS_nTor] = SAS_it[i];
                    SAS_jp[SAS_nTor] = SAS_jt[i];
                    SAS_kp[SAS_nTor] = SAS_kt[i];
                    SAS_lp[SAS_nTor] = SAS_it[j];
                }
            }
        }
    }
    if(SAS_nTor > 5*SAS_maxBonds)
    {
        printf(" Too many torsions .... Max = %d \n",3*SAS_maxBonds);
        exit(EXIT_FAILURE);
    }
    
	/*___________________________________________________________________________*/
    /* calculate now overlapping atoms. 
      2 species overlapping if dist < RADATM(i) + RADATM(j) + 2*RSOLV
      if RADS specified in input, 2 species overlapping if dist < 20.0 */
 
    SAS_nNonBon = 0;
    for(i = 1;i<=SAS_nAt-1;i++)
    {
        for(j = i+1;j<=SAS_nAt;j++)
        {
	        i3 = (i-1) * 3;
	        j3 = (j-1) * 3;
	        dx = SAS_aCoord[i3+1] - SAS_aCoord[j3+1];
	        dy = SAS_aCoord[i3+2] - SAS_aCoord[j3+2];
            dz = SAS_aCoord[i3+3] - SAS_aCoord[j3+3];
            rr = sqrt (dx*dx + dy*dy + dz*dz);
            radAtmi = SAS_ARADIUS[SAS_rType[i]][SAS_aType[i]];
            radAtmj = SAS_ARADIUS[SAS_rType[j]][SAS_aType[j]];
            cutoff = radAtmi + radAtmj + 2.0*SAS_RSOLV;
	      
	        if(rr < cutoff)
            {
                  klj = 0;
                  for(n = 1;n<=SAS_nBon;n++)
                  {
                      if((i == SAS_ib[n]  &&  j == SAS_jb[n])  || 
                          (j == SAS_ib[n]  &&  i == SAS_jb[n])) 
                          klj = 1;
                  }
                  for(n = 1;n<=SAS_nAng;n++)
                  {
                      if((i == SAS_it[n]  &&  j == SAS_kt[n])  || 
                          (j == SAS_it[n]  &&  i == SAS_kt[n]))
                          klj = 1;
                  }
                  for(n = 1;n<=SAS_nTor;n++)
                  {
                      if((i == SAS_ip[n]  &&  j == SAS_lp[n])  || 
                          (j == SAS_ip[n]  &&  i == SAS_lp[n]))
                          klj = 1;
                  }
                  if(klj == 0)
                  {
                      SAS_nNonBon ++;
                      SAS_in[SAS_nNonBon] = i;
                      SAS_jn[SAS_nNonBon] = j; 
                  }
              }
         }
    }
    if(SAS_nNonBon > 50*SAS_maxBonds)
    {
        printf(" Too many overlapping atoms .... Max = %d\n",50*SAS_maxBonds);
        exit(EXIT_FAILURE);
    }
}/* end of void topoly() */

/*___________________________________________________________________________*/
/* SASA calculation */
void calc_mol_sasa()
{
    int i;
    double radAtm;
    double pCon;
    
    /* initializing stuff for sasa */
    for(i=1;i<=SAS_nAt;i++)
    {
        radAtm = SAS_ARADIUS[SAS_rType[i]][SAS_aType[i]];
        SAS_aTotSurface[i] = 4.*SAS_PI*(radAtm+SAS_RSOLV)*(radAtm+SAS_RSOLV);
        SAS_aSasa[i] = SAS_aTotSurface[i];
	    SAS_nOverlap[i] = 0;
    }

    /* end initializing - start area calculation */
    
     /* loop over 1-2 interactions */
    for(i=1;i<=SAS_nBon;i++)
    {
        pCon=SAS_PCON12;
        calc_atomic_sasa(pCon,SAS_ib[i],SAS_jb[i]);
    }
    /* loop over 1-3 interactions */
    for(i=1;i<=SAS_nAng;i++)
    {
        pCon=SAS_PCON13;
        calc_atomic_sasa(pCon,SAS_it[i],SAS_kt[i]);
    }
     /* loop over 1-4 interactions */
    for(i=1;i<=SAS_nTor;i++)
    {
        pCon=SAS_PCON14;
        calc_atomic_sasa(pCon,SAS_ip[i],SAS_lp[i]);
    }
    /* loop over > 1-4 interactions */
    for (i=1;i<=SAS_nNonBon;i++)
    {
        pCon=SAS_PCON15;
        calc_atomic_sasa(pCon,SAS_in[i],SAS_jn[i]);
    }
}/* end of sasder() */

/*___________________________________________________________________________*/
/* SASA change from one contact */

void calc_atomic_sasa (double pCon,int i,int j)
{
    double radAtmi,radAtmj,paraAtmi,paraAtmj;
    double rr,dd,dx,dy,dz;
    int i3,j3;
    double ffi,ffj,ci1,cj1,cc2,ci3,cj3,bij,bji,ci,cj;

    /* initializing stuff for sasa */   
    radAtmi = SAS_ARADIUS[SAS_rType[i]][SAS_aType[i]];
    radAtmj = SAS_ARADIUS[SAS_rType[j]][SAS_aType[j]];

    paraAtmi = SAS_APARA[SAS_rType[i]][SAS_aType[i]];
    paraAtmj = SAS_APARA[SAS_rType[j]][SAS_aType[j]];

    i3 = (i-1) * 3;
    j3 = (j-1) * 3;
    dx = SAS_aCoord[i3+1] - SAS_aCoord[j3+1];
    dy = SAS_aCoord[i3+2] - SAS_aCoord[j3+2];
    dz = SAS_aCoord[i3+3] - SAS_aCoord[j3+3];
    rr = sqrt (dx*dx + dy*dy + dz*dz);
    dd = radAtmi + radAtmj + 2.0*SAS_RSOLV;

    /* if the two CA do not overlap. Return */
    if (dd < rr) return;
    
    ffi = 1.0;
    ffj = 1.0;
    ci1 = SAS_PI * (radAtmi + SAS_RSOLV);
    cj1 = SAS_PI * (radAtmj + SAS_RSOLV);

    cc2 = (dd - rr); /*  ** 1.00,  powered to 1 in the original file */
    /* something to be done with the '**' here. Why it's 1.0?  K.Lin */
    ci3 = (1.0 + (radAtmj - radAtmi)/rr);
    cj3 = (1.0 + (radAtmi - radAtmj)/rr);
    
    bij =  ffi * ci1 * cc2 * ci3;
    bji =  ffj * cj1 * cc2 * cj3;

    ci  = pCon * bij;
    cj  = pCon * bji;

    SAS_nOverlap[i]++;
    SAS_nOverlap[j]++;
    SAS_aSasa[i] = SAS_aSasa[i] * (1.0 - ci * paraAtmi / SAS_aTotSurface[i]);
    SAS_aSasa[j] = SAS_aSasa[j] * (1.0 - cj * paraAtmj / SAS_aTotSurface[j]);
} /* end of void calc_atomic_sasa (double pCon,int i,int j)*/

void assign_areas(vector<atom>& A, int& n)
{
    int j = 1;
    for(size_t i=0;i<A.size();i++)
    {
        if(A[i].atype.at(0) == 'H') continue;
        if(SAS_aSasa[j] > 1.)
        {
            A[i].satom = 1;
            n++;
        }
        j++;
    }
}
