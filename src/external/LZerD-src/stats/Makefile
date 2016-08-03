target   = lzerd_capri_stats
cc_objects = utils.o read.o contact.o rmsd.o align.o main.o
objects  = $(cc_objects)
CXX      = g++ 
DEPFLAGS = -MM 
ifeq ($(UNAME),Linux)
CXXFLAGS = -O2 -static -Wall -I$(HOME)/libraries/ann/include -I$(HOME)/libraries/gsl/include 
else
CXXFLAGS = -O2 -Wall -I$(HOME)/libraries/ann/include -I$(HOME)/libraries/gsl/include 
endif
LIBS     = -L$(HOME)/libraries/gsl/lib -lgsl -lgslcblas -L$(HOME)/libraries/ann/lib -lANN -lm
LDFLAGS  =

# everything below here is automatic
$(target) : $(objects)
	$(CXX) $(CXXFLAGS) -o $@ $(objects) $(LDFLAGS) $(LIBS)

depends = $(objects:.o=.d)

-include $(depends)

%.d : %.cc
	$(CXX) $(DEPFLAGS) $(CXXFLAGS) $< \
      | sed -e 's,\($*\)\.o:,\1.o $@ : ,g' > $@


.PHONY : clean
clean :
	-rm -f $(target) $(objects) $(depends)



