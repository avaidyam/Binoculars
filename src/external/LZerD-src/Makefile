# vdock
target   = LZerD
cc_objects = gh.o ptalign.o score.o matrix.o utils.o checkalign.o zd.o read.o options.o main.o
mpicc_objects = gh_mpi.o ptalign.o score.o matrix.o utils.o checkalign.o zd.o read.o options.o main_mpi.o
objects  = $(cc_objects)
ifeq ($(UNAME),Linux)
CXX =  g++ -static
else
CXX =  g++
endif
MPICPP=mpiCC.mpich
CXXFLAGS = -O3 -Wall -I$(HOME)/libraries/gsl/include -I$(HOME)/libraries/ann/include
DEPFLAGS = -MM 
LIBS = -L$(HOME)/libraries/gsl/lib -lgsl -lgslcblas -lm -L$(HOME)/libraries/ann/lib -lANN
LDFLAGS  =

ifeq ($(UNAME),Linux)
all: $(target) LZerD_mpi
else
all: $(target)
endif

# everything below here is automatic
$(target) : $(objects)
	$(CXX) $(CXXFLAGS) -o $@ $(objects) $(LDFLAGS) $(LIBS)

LZerD_mpi: $(mpicc_objects)
	$(MPICPP) -DWITH_MPI $(CXXFLAGS) $(LIBS) $(mpicc_objects) $(LDFLAGS) -o $@

depends = $(objects:.o=.d)

-include $(depends)

%.d : %.cc
	$(CXX) $(DEPFLAGS) $(CXXFLAGS) $< \
      | sed -e 's,\($*\)\.o:,\1.o $@ : ,g' > $@

main_mpi.o: main.cc
	$(MPICPP) -DWITH_MPI $(CXXFLAGS) -c $(subst _mpi.o,.cc,$@) -o $@

gh_mpi.o: gh.cc
	$(MPICPP) -DWITH_MPI $(CXXFLAGS) -c $(subst _mpi.o,.cc,$@) -o $@

.PHONY : clean
clean :
	-rm -f $(target) $(objects) $(mpicc_objects) LZerD_mpi $(depends)



