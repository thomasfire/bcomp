package Machine;

enum RUNSTATES {
	STOPPED, RUNNING, STOPPING
}

/**
* Базовая ЭВМ (управление работой)
* @author Ponomarev
*/
public class Machine implements Runnable
{
	public Machine(ControlView ctrl)
	{
		this.ctrl=ctrl;
		
		// Фабрика регистров
		reg_factory = new RegisterFactory();
		
		// Основная память
		memory = new Memory(reg_factory);

		// Память микрокомманд
		micro_mem = new MicrocommandMemory(reg_factory);
		
		// Фабрика флагов
		flags = new FlagFactory(reg_factory);
		
		// Фабрика Каналов
		channels = new ChannelFactory(reg_factory, memory, micro_mem);
		
		// АЛУ
		alu = new ALU(reg_factory, flags);
		
		// Устройства ввода/вывода
		dev = new DeviceFactory(reg_factory);
		
		// Устройство управления
		man_dev = new ManagerDevice(reg_factory, channels, alu, flags, dev);

		// Объекты для блокировок
		lock_wait = new Object();
		lock_continue = new Object();

		// Запускаем поток для БЭВМ
		runstate = RUNSTATES.STOPPED;
		Thread t = new Thread(this);
		t.start();
	}

	/**
	 * Поток, отвечающий за работу БЭВМ
	 */
	public void run()
	{
		for (;;) {
			runstate = RUNSTATES.STOPPED;

			synchronized (lock_continue) {
				try {
					lock_continue.wait(); 
				} catch (Exception e) { }
			}

			runstate = RUNSTATES.RUNNING;
			for (;;) {
				man_dev.timeStep();
				ctrl.repaint();

				if (ctrl.getTact() || (runstate != RUNSTATES.RUNNING))
					break;
				
				int mcmd = reg_factory.getMicroCommandRegister().getValue();
				
				if (((mcmd & 0xc000) == 0x4000) && ((mcmd & 0x8) == 0x8))
					break;

				try	{
					Thread.sleep(10L);
				} catch (Exception e) {	}
			}

			if (runstate == RUNSTATES.STOPPING) {
				synchronized (lock_wait) {
					try {
						lock_wait.notifyAll();
					} catch (Exception e) { }
				}
			}
		}
	}

	/**
	 * "Продолжение"
	 */
	public void continuebasepc()
	{
		if (runstate != RUNSTATES.STOPPED)
			return;

		synchronized (lock_continue) {
			try {
				lock_continue.notifyAll();
			} catch (Exception e) { }
		}
	}

	/**
	 * Stop a running БЭВМ
	 */
	private void stopbasepc()
	{
		if (runstate == RUNSTATES.STOPPED)
			return;

		synchronized (runstate) {
			if (runstate == RUNSTATES.RUNNING)
				runstate = RUNSTATES.STOPPING;
		}

		synchronized (lock_wait) {
			try {
				lock_wait.wait();
			} catch (Exception e) { }
		}
	}

	/**
	 * Запустить БЭВМ начиная с указанного адреса микрокоманд
	 */
	private void startfrom(int addr)
	{
		stopbasepc();
		reg_factory.getMicroInstructionPointer().setValue(addr);
		continuebasepc();
	}

	/**
	 * "Пуск"
	 */
	public void start()
	{
		startfrom(0xA8);
	}
	
	/**
	 * "Ввод адреса"
	 */
	public void adress()
	{
		if (ctrl.microWork()) {
			reg_factory.getMicroInstructionPointer().setValue(reg_factory.getInputRegister().getValue());
			ctrl.repaint();
		} else
			startfrom(0x99);
	}
	
	/**
	 * "Чтение"
	 */
	public void read()
	{
		startfrom(0x9C);
	}
	
	/**
	 * "Запись"
	 */
	public void record()
	{
		if (ctrl.microWork()) {
			micro_mem.setValue(reg_factory.getInputRegister().getValue());
			reg_factory.getMicroInstructionPointer().setValue(reg_factory.getMicroInstructionPointer().getValue()+1);
			ctrl.repaint();
		} else
			startfrom(0xA1);
	}
	
	/**
	 * "Работа останов"
	 */
	public void workStop()
	{
		if (flags.getStateOfTumbler().getValue() == 0)
		{
			flags.getStateOfTumbler().setValue(1);
		}
		else
		{
			flags.getStateOfTumbler().setValue(0);
		}
		flags.refreshStateCounter();
		ctrl.repaint();
	}
	
	/**
	 * Получить фабрику регистров
	 * @return Возвращает фабрику регистров
	 */
	public RegisterFactory getRegFactory()
	{
		return reg_factory;
	}
	
	/**
	 * Получить основную память
	 * @return Возвращает основную память
	 */
	public Memory getMemory()
	{
		return memory;
	}
	
	/**
	 * Получить память микрокоманд
	 * @return Возвращает память микрокоманд
	 */
	public MicrocommandMemory getMicroMem()
	{
		return micro_mem;
	}
	
	/**
	 * Получить фабрику флагов
	 * @return Возвращает фабрику флагов
	 */
	public FlagFactory getFlagFactory()
	{
		return flags;
	}
	
	/**
	 * Получить фабрику каналов
	 * @return Возвращает фабрику каналов
	 */
	public ChannelFactory getChannelFactory()
	{
		return channels;
	}
	
	/**
	 * Получить АЛУ
	 * @return Возвращает АЛУ
	 */
	public ALU getALU()
	{
		return alu;
	}
	
	/**
	 * Получить устройство управление
	 * @return Возвращает Возвращает УУ
	 */
	public ManagerDevice getManagerDevice()
	{
		return man_dev;
	}
	
	/**
	 * Получить фабрику усройств
	 * @return Возвращает фабрику усройств
	 */
	public DeviceFactory getDeviceFactory()
	{
		return dev;
	}
	
	private RegisterFactory		reg_factory;
	private Memory				memory;
	private MicrocommandMemory	micro_mem;
	private FlagFactory			flags;
	private ChannelFactory		channels;
	private ALU					alu;
	private ManagerDevice		man_dev;
	private DeviceFactory 		dev;
	
	private ControlView			ctrl;
	private Object				lock_wait;
	private Object				lock_continue;

	private volatile RUNSTATES	runstate;
}
